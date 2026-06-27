/**
 * Status de um veículo do estoque do perfil concessionaria (camada 8.17) — espelho 1:1 de
 * src/main/java/com/meada/profiles/concessionaria/ConcessionariaVehicleStatus.java.
 *
 * O ConcessionariaVehicleStatusParityTest (backend) garante que os ids aqui e no enum Java nunca
 * divergem. A CHECK constraint de concessionaria_vehicles.status (migration) trava os mesmos ids.
 * id = string estável (persistida); label = rótulo pt-BR exibido.
 *
 * Ciclo do estoque:
 *   disponivel → reservado, vendido
 *   reservado  → disponivel, vendido
 *   vendido    → (terminal)
 */
export const VEHICLE_STATUSES = [
  { id: 'disponivel', label: 'Disponível' },
  { id: 'reservado', label: 'Reservado' },
  { id: 'vendido', label: 'Vendido' },
] as const

export type VehicleStatus = (typeof VEHICLE_STATUSES)[number]
export type VehicleStatusId = VehicleStatus['id']

/** Transições permitidas a partir de cada status (espelha ConcessionariaVehicleStatus.allowedNext). */
export const ALLOWED_NEXT: Record<VehicleStatusId, VehicleStatusId[]> = {
  disponivel: ['reservado', 'vendido'],
  reservado: ['disponivel', 'vendido'],
  vendido: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return VEHICLE_STATUSES.find((s) => s.id === id)?.label ?? id
}
