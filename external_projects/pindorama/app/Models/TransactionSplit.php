<?php
namespace App\Models;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
class TransactionSplit extends Model
{
    protected $fillable=['transaction_id','professional_id','percentage','amount'];
    protected $casts=['percentage'=>'decimal:2','amount'=>'decimal:2'];
    public function transaction(): BelongsTo{return $this->belongsTo(Transaction::class);}
    public function professional(): BelongsTo{return $this->belongsTo(User::class,'professional_id');}
}
