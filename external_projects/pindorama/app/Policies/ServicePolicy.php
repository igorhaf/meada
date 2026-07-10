<?php

namespace App\Policies;

use App\Models\Service;
use App\Models\User;

class ServicePolicy
{
    /** Root manages everything; professionals manage only their own services. */
    public function manage(User $user, Service $service): bool
    {
        return $user->isRoot() || $service->professional_id === $user->id;
    }

    public function update(User $user, Service $service): bool
    {
        return $this->manage($user, $service);
    }

    public function delete(User $user, Service $service): bool
    {
        return $this->manage($user, $service);
    }
}
