<script setup>
import { ref, computed, watch, onMounted } from 'vue';

const props = defineProps({
    serviceId: { type: Number, required: true },
    durationMinutes: { type: Number, default: 60 },
    requiresPrepayment: { type: Boolean, default: true },
    locations: { type: Array, default: () => [] },
    isAuthenticated: { type: Boolean, default: false },
    loginUrl: { type: String, default: '/entrar' },
    slotsUrl: { type: String, default: '/api/agenda/horarios' },
    bookingUrl: { type: String, default: '/agendar' },
    csrf: { type: String, default: '' },
    patient: { type: Object, default: () => ({ name: '', email: '' }) },
    privacyUrl: { type: String, default: '/privacidade' },
});

const locationId = ref(props.locations.length ? props.locations[0].id : null);
const date = ref('');
const slots = ref([]);
const time = ref('');
const loading = ref(false);
const loaded = ref(false);
const name = ref(props.patient.name || '');
const email = ref(props.patient.email || '');
const phone = ref('');
const notes = ref('');
const submitting = ref(false);
const consent = ref(false);

const today = new Date().toISOString().slice(0, 10);

const canSubmit = computed(() => locationId.value && date.value && time.value && name.value.trim() && consent.value);

watch([locationId, date], fetchSlots);
onMounted(() => { if (props.locations.length === 1) fetchSlots(); });

async function fetchSlots() {
    time.value = '';
    slots.value = [];
    loaded.value = false;
    if (!locationId.value || !date.value) return;
    loading.value = true;
    try {
        const url = `${props.slotsUrl}?service=${props.serviceId}&location=${locationId.value}&date=${date.value}`;
        const res = await fetch(url, { headers: { Accept: 'application/json' } });
        const data = await res.json();
        slots.value = data.slots || [];
    } catch (e) {
        slots.value = [];
    } finally {
        loading.value = false;
        loaded.value = true;
    }
}

function submit() {
    if (!props.isAuthenticated) {
        window.location.href = props.loginUrl;
        return;
    }
    if (!canSubmit.value) return;
    submitting.value = true;
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = props.bookingUrl;
    const fields = {
        _token: props.csrf,
        service_id: props.serviceId,
        attendance_location_id: locationId.value,
        date: date.value,
        time: time.value,
        patient_name: name.value,
        patient_email: email.value,
        patient_phone: phone.value,
        notes: notes.value,
        health_data_consent: consent.value ? '1' : '',
    };
    for (const [k, v] of Object.entries(fields)) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = k;
        input.value = v ?? '';
        form.appendChild(input);
    }
    document.body.appendChild(form);
    form.submit();
}
</script>

<template>
    <div class="space-y-4">
        <h3 class="text-sm font-bold uppercase tracking-wide text-neutral-500">Agendar</h3>

        <div v-if="!locations.length" class="text-sm text-neutral-500">
            Este serviço ainda não tem locais de atendimento disponíveis.
        </div>

        <template v-else>
            <!-- Local -->
            <div v-if="locations.length > 1">
                <label class="mb-1 block text-xs font-medium text-neutral-600">Local</label>
                <select v-model="locationId" class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm">
                    <option v-for="l in locations" :key="l.id" :value="l.id">{{ l.isOnline ? '💻' : '📍' }} {{ l.name }}</option>
                </select>
            </div>
            <p v-else class="text-sm text-neutral-600">{{ locations[0].isOnline ? '💻' : '📍' }} {{ locations[0].name }}</p>

            <!-- Data -->
            <div>
                <label class="mb-1 block text-xs font-medium text-neutral-600">Data</label>
                <input type="date" v-model="date" :min="today" class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm" />
            </div>

            <!-- Horários -->
            <div v-if="date">
                <p v-if="loading" class="text-sm text-neutral-500">Buscando horários…</p>
                <p v-else-if="loaded && !slots.length" class="text-sm text-neutral-500">Nenhum horário disponível nesta data.</p>
                <div v-else-if="slots.length" class="flex flex-wrap gap-2">
                    <button v-for="s in slots" :key="s" type="button" @click="time = s"
                        :class="['rounded-lg border px-3 py-1.5 text-sm', time === s ? 'border-brand-600 bg-brand-600 text-white' : 'border-neutral-300 text-neutral-700 hover:border-brand-400']">
                        {{ s }}
                    </button>
                </div>
            </div>

            <!-- Dados do paciente + confirmar -->
            <div v-if="time" class="space-y-3 border-t border-neutral-100 pt-4">
                <input v-model="name" placeholder="Seu nome" class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm" />
                <input v-model="phone" placeholder="Telefone / WhatsApp" class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm" />
                <textarea v-model="notes" rows="2" placeholder="Motivo / observações (opcional)" class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm"></textarea>
                <label class="flex items-start gap-2 text-xs text-neutral-600"><input v-model="consent" type="checkbox" class="mt-0.5"><span>Autorizo o tratamento dos dados informados para realizar o atendimento, conforme a <a :href="privacyUrl" class="text-brand-700 underline">política de privacidade</a>.</span></label>
                <p v-if="requiresPrepayment" class="text-xs text-neutral-500">💳 Pagamento antecipado para confirmar o horário.</p>
            </div>

            <button type="button" @click="submit" :disabled="(isAuthenticated && !canSubmit) || submitting"
                class="btn-brand w-full disabled:cursor-not-allowed disabled:opacity-50">
                {{ !isAuthenticated ? 'Entrar para agendar' : (requiresPrepayment ? 'Agendar e pagar' : 'Solicitar agendamento') }}
            </button>
        </template>
    </div>
</template>
