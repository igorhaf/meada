<?php

namespace App\Exceptions;

use RuntimeException;

/** Event has no spots left (→ 409). */
class EventFullException extends RuntimeException
{
    public string $reason = 'sold_out';

    public function __construct(string $message = 'As vagas para este evento esgotaram.')
    {
        parent::__construct($message);
    }
}
