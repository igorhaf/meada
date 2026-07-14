<?php
namespace App\Models;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
class CustomerInvite extends Model
{
    protected $fillable=['customer_id','token_hash','expires_at','accepted_at','created_by'];protected $casts=['expires_at'=>'datetime','accepted_at'=>'datetime'];public function customer():BelongsTo{return $this->belongsTo(User::class,'customer_id');}
}
