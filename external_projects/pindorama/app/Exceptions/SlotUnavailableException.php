<?php

namespace App\Exceptions;

use RuntimeException;

/** The requested slot conflicts with another appointment on the professional's agenda (→ 409). */
class SlotUnavailableException extends RuntimeException
{
    public string $reason = 'conflict_slot';

    public function __construct(string $message = 'Este horário acabou de ser reservado. Escolha outro.')
    {
        parent::__construct($message);
    }
}
