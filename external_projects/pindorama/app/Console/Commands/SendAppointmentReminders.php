<?php
namespace App\Console\Commands;
use App\Models\Appointment;
use App\Services\NotificationService;
use Illuminate\Console\Command;
class SendAppointmentReminders extends Command
{
    protected $signature='appointments:remind {--hours=24}';protected $description='Envia lembretes das consultas confirmadas';
    public function handle(NotificationService $notifications):int{$until=now()->addHours((int)$this->option('hours'));$appointments=Appointment::where('status','confirmed')->whereNull('reminded_at')->whereBetween('start_at',[now(),$until])->get();foreach($appointments as $appointment){$notifications->appointmentReminder($appointment);$appointment->update(['reminded_at'=>now()]);}$this->info($appointments->count().' lembretes enviados.');return self::SUCCESS;}
}
