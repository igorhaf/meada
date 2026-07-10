<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class SiteSetting extends Model
{
    protected $fillable = [
        'site_name', 'tagline', 'announcement',
        'instagram_url', 'facebook_url', 'tiktok_url', 'whatsapp',
        'contact_email', 'contact_phone', 'address', 'opening_hours', 'about',
    ];

    /** Linha única memoizada por request. */
    protected static ?SiteSetting $current = null;

    /** A linha única de configuração (editada pelo root no painel). */
    public static function current(): self
    {
        return static::$current ??= static::query()->first() ?? new static([
            'site_name' => 'Semente Doce',
            'tagline' => 'Doces & salgados artesanais, feitos com carinho',
        ]);
    }

    /**
     * Links sociais preenchidos, para iterar no Blade.
     *
     * @return array<string,string>
     */
    public function socialLinks(): array
    {
        return array_filter([
            'instagram' => $this->instagram_url,
            'facebook' => $this->facebook_url,
            'tiktok' => $this->tiktok_url,
            'whatsapp' => $this->whatsapp ? 'https://wa.me/' . preg_replace('/\D/', '', $this->whatsapp) : null,
        ]);
    }
}
