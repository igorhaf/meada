<?php

namespace App\Http\Controllers;

use App\Exceptions\OutsideHoursException;
use App\Exceptions\SlotUnavailableException;
use App\Models\AttendanceLocation;
use App\Models\Service;
use App\Services\BookingService;
use App\Services\NotificationService;
use Carbon\CarbonImmutable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class BookingController extends Controller
{
    public function __construct(private BookingService $booking) {}

    /** JSON: free start times for a service + location + date (public, for the SlotPicker). */
    public function slots(Request $request): JsonResponse
    {
        $data = $request->validate([
            'service' => ['required', 'integer'],
            'location' => ['required', 'integer'],
            'date' => ['required', 'date_format:Y-m-d'],
        ]);

        $service = Service::active()->findOrFail($data['service']);
        $professional = $service->professional;
        $location = AttendanceLocation::where('professional_id', $professional->id)->findOrFail($data['location']);

        // Só locais que oferecem o serviço.
        if (! $service->locations()->whereKey($location->id)->exists()) {
            return response()->json(['slots' => []]);
        }

        $tz = $professional->timezone ?: config('pindorama.timezone');
        $date = CarbonImmutable::parse($data['date'], $tz);

        return response()->json([
            'date' => $data['date'],
            'location_id' => $location->id,
            'slots' => $this->booking->availableSlots($professional, $service, $location, $date),
        ]);
    }

    /** Create the appointment (auth). Server recomputes price; conflict-checked. */
    public function store(Request $request, NotificationService $notifications): RedirectResponse
    {
        $data = $request->validate([
            'service_id' => ['required', 'integer'],
            'attendance_location_id' => ['required', 'integer'],
            'date' => ['required', 'date_format:Y-m-d'],
            'time' => ['required', 'date_format:H:i'],
            'patient_name' => ['required', 'string', 'max:255'],
            'patient_email' => ['nullable', 'email', 'max:255'],
            'patient_phone' => ['nullable', 'string', 'max:40'],
            'notes' => ['nullable', 'string', 'max:2000'],
            'health_data_consent' => ['accepted'],
        ]);

        $service = Service::active()->findOrFail($data['service_id']);
        $professional = $service->professional;
        $location = AttendanceLocation::where('professional_id', $professional->id)->findOrFail($data['attendance_location_id']);

        $tz = $professional->timezone ?: config('pindorama.timezone');
        $start = CarbonImmutable::parse("{$data['date']} {$data['time']}", $tz);

        try {
            $appointment = $this->booking->book($professional, $service, $location, $start, [
                'name' => $data['patient_name'],
                'email' => $data['patient_email'] ?? null,
                'phone' => $data['patient_phone'] ?? null,
                'notes' => $data['notes'] ?? null,
                'consent' => true,
            ], $request->user());
        } catch (OutsideHoursException $e) {
            return back()->withInput()->with('error', $e->getMessage());
        } catch (SlotUnavailableException $e) {
            return back()->withInput()->with('error', $e->getMessage());
        }
        $notifications->appointmentCreated($appointment);

        // Pagamento (P8): se requires_prepayment e MP ligado → Brick; senão nasce pending
        // aguardando aceite do terapeuta. Por ora, segue para o agendamento.
        if ($service->requires_prepayment && config('services.mercadopago.enabled')) {
            return redirect()->route('payment.show', $appointment);
        }

        return redirect()->route('appointments.show', $appointment)
            ->with('status', 'Agendamento solicitado! O terapeuta irá confirmar em breve.');
    }
}
