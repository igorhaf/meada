<?php

namespace App\Models;

// use Illuminate\Contracts\Auth\MustVerifyEmail;
use Database\Factories\UserFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;

#[Fillable([
    'name', 'email', 'password', 'role', 'is_professional',
    'professional_name', 'professional_slug', 'headline', 'bio', 'city', 'state',
    'phone', 'whatsapp', 'avatar_path', 'banner_path', 'brand_primary', 'brand_secondary',
    'timezone', 'registration_council', 'is_verified',
    'billing_monthly_fee', 'billing_discount_percent', 'billing_free', 'billing_active', 'billing_day',
    'google_id', 'avatar',
])]
#[Hidden(['password', 'remember_token'])]
class User extends Authenticatable
{
    /** @use HasFactory<UserFactory> */
    use HasFactory, Notifiable;

    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
            'is_professional' => 'boolean',
            'is_verified' => 'boolean',
            'billing_monthly_fee' => 'decimal:2',
            'billing_discount_percent' => 'decimal:2',
            'billing_free' => 'boolean',
            'billing_active' => 'boolean',
        ];
    }

    /* ---------------------------------------------------------------- Relations */

    /** Services this professional offers (their tenant catalog). */
    public function services(): HasMany
    {
        return $this->hasMany(Service::class, 'professional_id');
    }

    /** Appointments this user booked as a customer/patient. */
    public function appointmentsAsCustomer(): HasMany
    {
        return $this->hasMany(Appointment::class, 'customer_id');
    }

    /** Appointments on this professional's agenda (all their locations). */
    public function appointmentsAsProfessional(): HasMany
    {
        return $this->hasMany(Appointment::class, 'professional_id');
    }

    /** Practice types this professional specializes in (chips + directory facet). */
    public function specialties(): BelongsToMany
    {
        return $this->belongsToMany(ServiceCategory::class, 'professional_specialties', 'professional_id', 'service_category_id');
    }

    /** Attendance locations (physical consultórios + "Online") — all share one agenda. */
    public function attendanceLocations(): HasMany
    {
        return $this->hasMany(AttendanceLocation::class, 'professional_id');
    }

    /** Weekly working hours (per location). */
    public function availabilities(): HasMany
    {
        return $this->hasMany(ProfessionalAvailability::class, 'professional_id');
    }

    /** Days off / exceptions. */
    public function availabilityBlocks(): HasMany
    {
        return $this->hasMany(AvailabilityBlock::class, 'professional_id');
    }

    /** Platform charges billed to this professional (subscription/registration/featured). */
    public function charges(): HasMany
    {
        return $this->hasMany(PlatformCharge::class, 'professional_id');
    }

    /** Events (rodas/cursos/certificações) hosted by this professional. */
    public function events(): HasMany
    {
        return $this->hasMany(Event::class, 'professional_id');
    }

    /* ------------------------------------------------------------------- Roles */

    public function isRoot(): bool
    {
        return $this->role === 'root';
    }

    public function isProfessional(): bool
    {
        return $this->is_professional || $this->isRoot();
    }

    /** Public landing URL for a therapist (wired in P4 when professional_slug exists). */
    public function getProfessionalUrlAttribute(): ?string
    {
        return $this->professional_slug ? route('professionals.show', $this->professional_slug) : null;
    }

    public function getInitialAttribute(): string
    {
        return mb_strtoupper(mb_substr($this->professional_name ?: $this->name, 0, 1));
    }

    public function getDisplayNameAttribute(): string
    {
        return $this->professional_name ?: $this->name;
    }

    public function getAvatarUrlAttribute(): ?string
    {
        return $this->avatar_path ?: ($this->avatar ?: null);
    }

    public function getBannerUrlAttribute(): ?string
    {
        return $this->banner_path ?: null;
    }
}
