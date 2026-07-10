<?php

namespace App\Exceptions;

use RuntimeException;

/** The requested time falls outside the professional's availability at that location (→ 422). */
class OutsideHoursException extends RuntimeException
{
    public string $reason = 'outside_hours';

    public function __construct(string $message = 'Este horário não está disponível para agendamento.')
    {
        parent::__construct($message);
    }
}
