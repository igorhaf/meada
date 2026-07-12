<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class TelegramController extends Controller
{
    /** Gera o código que o usuário manda pro bot via /vincular CODIGO. */
    public function linkCode(Request $request): JsonResponse
    {
        $user = $request->user();
        $code = strtoupper(Str::random(6));
        $user->forceFill(['telegram_link_code' => $code])->save();

        return response()->json([
            'code' => $code,
            'bot_username' => 'RosendoFrancaBot',
            'linked' => $user->telegram_chat_id !== null,
        ]);
    }
}
