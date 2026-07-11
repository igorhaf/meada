"""
Elo — Pipeline Planner.

Estimates token cost of a Deep Pipeline run for a given project, compares
against remaining quota in the current 5h window, and proposes an adjusted
profile when the run won't fit.

Modes:
  aggressive   — proceed if estimate <= remaining * 1.0   (may exhaust quota)
  balanced     — proceed if estimate <= remaining * 0.7   (default)
  conservative — proceed if estimate <= remaining * 0.5

When fits == False:
  adapt_profile() returns an adjusted profile with reduced batch sizes,
  Sonnet->Haiku swaps on non-critical phases, and lower max_tokens.

Estimates are heuristic averages (calibrated via pipeline_runs history).
"""
import logging
from dataclasses import dataclass, asdict, field
from typing import Optional

logger = logging.getLogger("elo.pipeline_planner")


# Per-phase heuristic costs (input + output tokens per unit-of-work).
# Calibrated against averages from past pipeline_runs; refine over time.
PHASE_HEURISTICS = {
    "phase_0": {"in_per_unit": 0,      "out_per_unit": 0,    "unit": "files"},
    # phase_1 batches ~10 files/prompt (v3.7.7): the system-prompt overhead is
    # amortized across the group, so per-file input drops from ~4000 to ~2500.
    "phase_1": {"in_per_unit": 2_500,  "out_per_unit": 1_000, "unit": "files"},
    "phase_2": {"in_per_unit": 12_000, "out_per_unit": 8_000, "unit": "domains"},
    "phase_3": {"in_per_unit": 50_000, "out_per_unit": 16_000, "unit": "fixed_1"},
    "phase_4a": {"in_per_unit": 8_000,  "out_per_unit": 4_000, "unit": "epics_per_batch"},
    "phase_4b": {"in_per_unit": 6_000,  "out_per_unit": 3_000, "unit": "stories_per_epic"},
    "phase_4c": {"in_per_unit": 4_000,  "out_per_unit": 2_000, "unit": "tasks_per_story"},
    "phase_5":  {"in_per_unit": 6_000,  "out_per_unit": 3_000, "unit": "pages"},
    "phase_6":  {"in_per_unit": 12_000, "out_per_unit": 4_000, "unit": "fixed_1"},
}

MODE_FACTORS = {
    "aggressive":   1.0,
    "balanced":     0.7,
    "conservative": 0.5,
}


@dataclass
class PhaseEstimate:
    phase: str
    units: int
    input_tokens: int
    output_tokens: int


@dataclass
class PlanEstimate:
    total_input: int
    total_output: int
    by_phase: list[PhaseEstimate] = field(default_factory=list)


@dataclass
class PlanResult:
    mode: str
    fits: bool
    recommendation: str          # 'proceed' | 'adjust' | 'wait'
    estimate: PlanEstimate
    remaining_input: int
    remaining_output: int
    budget_input: int            # remaining * mode_factor
    budget_output: int
    suggested_profile: Optional[dict] = None
    suggested_mode: Optional[str] = None
    reason: str = ""


def _default_units(project_meta: dict) -> dict[str, int]:
    """Reasonable defaults if project hasn't been scanned yet."""
    n_files = int(project_meta.get("n_files", 256) or 0) or 50
    n_domains = int(project_meta.get("n_domains", 0) or 0) or max(8, n_files // 30)
    n_epics = int(project_meta.get("n_epics", 0) or 0) or max(5, n_domains // 2)
    n_stories_per_epic = 6
    n_tasks_per_story = 3
    n_pages = max(8, n_domains)
    return {
        "files": n_files,
        "domains": n_domains,
        "epics_per_batch": n_epics,
        "stories_per_epic": n_epics * n_stories_per_epic,
        "tasks_per_story": n_epics * n_stories_per_epic * n_tasks_per_story,
        "pages": n_pages,
        "fixed_1": 1,
    }


def estimate_pipeline_cost(project_meta: dict, profile: Optional[dict] = None) -> PlanEstimate:
    """Estimate total tokens for a deep pipeline run.

    project_meta: dict with keys like n_files, n_domains, n_epics. Missing keys
                  are filled with defaults proportional to n_files.
    profile:      optional pipeline profile (currently ignored; v2.4 follow-up).
    """
    units = _default_units(project_meta)
    # Resume-aware: skip phases already completed (a resume re-runs only the
    # pending ones). last_completed_phase is the highest int phase done (e.g. 1
    # means phase_0 and phase_1 are done → estimate only phase_2 onward).
    last_completed = int(project_meta.get("last_completed_phase", -1) or -1)
    by_phase: list[PhaseEstimate] = []
    total_in = 0
    total_out = 0
    for phase, h in PHASE_HEURISTICS.items():
        # phase keys: phase_0, phase_1, phase_2, phase_3, phase_4a, ... — extract
        # the leading int to compare against last_completed.
        try:
            pnum = int("".join(c for c in phase.split("phase_")[1] if c.isdigit()))
        except Exception:
            pnum = 99
        if pnum <= last_completed:
            by_phase.append(PhaseEstimate(phase=phase, units=0, input_tokens=0, output_tokens=0))
            continue
        n = units.get(h["unit"], 0)
        in_t = n * h["in_per_unit"]
        out_t = n * h["out_per_unit"]
        by_phase.append(PhaseEstimate(phase=phase, units=n, input_tokens=in_t, output_tokens=out_t))
        total_in += in_t
        total_out += out_t
    return PlanEstimate(total_input=total_in, total_output=total_out, by_phase=by_phase)


def fits_in_budget(estimate: PlanEstimate, remaining_in: int, remaining_out: int, mode: str) -> bool:
    factor = MODE_FACTORS.get(mode, 0.7)
    budget_in = int(remaining_in * factor)
    budget_out = int(remaining_out * factor)
    return estimate.total_input <= budget_in and estimate.total_output <= budget_out


def adapt_profile(estimate: PlanEstimate, remaining_in: int, remaining_out: int, mode: str) -> Optional[dict]:
    """Propose a reduced profile when the pipeline doesn't fit.

    Returns a dict with overrides to apply on top of the base profile, or
    None if no adjustment can make it fit even on conservative mode.
    """
    factor = MODE_FACTORS.get(mode, 0.7)
    budget_in = int(remaining_in * factor)
    budget_out = int(remaining_out * factor)
    if estimate.total_input == 0 or estimate.total_output == 0:
        return None
    in_ratio = budget_in / max(1, estimate.total_input)
    out_ratio = budget_out / max(1, estimate.total_output)
    scale = min(in_ratio, out_ratio)

    if scale >= 1.0:
        return None  # already fits

    if scale < 0.20:
        return None  # too far gone; user should wait

    # Build override profile that scales heavy phases down
    overrides = {
        "phase_1": {"batch_size": max(2, int(10 * scale))},
        "phase_2": {"concurrency": max(1, int(5 * scale)), "model": "claude-haiku-4-5" if scale < 0.5 else None},
        "phase_3": {"thinking_budget": int(10000 * scale)},
        "phase_4a": {"max_tokens": int(16000 * scale)},
        "phase_4b": {"max_tokens": int(32000 * scale), "concurrency": max(1, int(3 * scale))},
        "phase_4c": {"max_tokens": int(8000 * scale)},
        "phase_5": {"enabled_subphases": ["5a", "5b"] if scale < 0.5 else None},
        "phase_6": {"enabled": scale > 0.3},
    }
    # Strip None values
    return {k: {kk: vv for kk, vv in v.items() if vv is not None} for k, v in overrides.items()}


def plan(project_meta: dict, remaining_in: int, remaining_out: int, mode: str = "balanced",
         profile: Optional[dict] = None) -> PlanResult:
    """Top-level planner. Returns a PlanResult ready to serialize."""
    if mode not in MODE_FACTORS:
        mode = "balanced"
    factor = MODE_FACTORS[mode]
    budget_in = int(remaining_in * factor)
    budget_out = int(remaining_out * factor)

    estimate = estimate_pipeline_cost(project_meta, profile)
    fits = fits_in_budget(estimate, remaining_in, remaining_out, mode)

    recommendation = "proceed"
    suggested_profile = None
    suggested_mode = None
    reason = ""

    if not fits:
        # Try conservative if user picked balanced; try adapt if already conservative.
        if mode != "conservative":
            cons_fits = fits_in_budget(estimate, remaining_in, remaining_out, "conservative")
            if cons_fits:
                recommendation = "adjust"
                suggested_mode = "conservative"
                reason = "Mude pra modo conservative pra caber dentro do orçamento."
            else:
                suggested_profile = adapt_profile(estimate, remaining_in, remaining_out, "conservative")
                if suggested_profile:
                    recommendation = "adjust"
                    suggested_mode = "conservative"
                    reason = "Mesmo em conservative, precisa reduzir batch sizes e trocar modelos."
                else:
                    recommendation = "wait"
                    reason = "Cota insuficiente em qualquer modo; aguarde reset."
        else:
            suggested_profile = adapt_profile(estimate, remaining_in, remaining_out, mode)
            if suggested_profile:
                recommendation = "adjust"
                reason = "Reduza batch sizes e troque Sonnets por Haiku nas fases não críticas."
            else:
                recommendation = "wait"
                reason = "Cota insuficiente mesmo após adaptações; aguarde reset."

    return PlanResult(
        mode=mode,
        fits=fits,
        recommendation=recommendation,
        estimate=estimate,
        remaining_input=remaining_in,
        remaining_output=remaining_out,
        budget_input=budget_in,
        budget_output=budget_out,
        suggested_profile=suggested_profile,
        suggested_mode=suggested_mode,
        reason=reason,
    )


def plan_to_dict(result: PlanResult) -> dict:
    """JSON-serializable representation."""
    return {
        "mode": result.mode,
        "fits": result.fits,
        "recommendation": result.recommendation,
        "reason": result.reason,
        "estimate": {
            "total_input": result.estimate.total_input,
            "total_output": result.estimate.total_output,
            "by_phase": [asdict(p) for p in result.estimate.by_phase],
        },
        "remaining": {
            "input": result.remaining_input,
            "output": result.remaining_output,
        },
        "budget": {
            "input": result.budget_input,
            "output": result.budget_output,
        },
        "suggested_profile": result.suggested_profile,
        "suggested_mode": result.suggested_mode,
    }
