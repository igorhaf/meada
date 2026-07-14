@extends('layouts.dashboard')
@section('title',($service->exists?'Editar':'Novo').' serviço · '.$professional->display_name)
@section('content')
<div class="mb-6"><a href="{{ route('admin.professionals.show',$professional) }}" class="text-sm text-neutral-500">← {{ $professional->display_name }}</a><h1 class="mt-2 text-2xl font-extrabold">{{ $service->exists?'Editar serviço':'Novo serviço' }}</h1></div>
@include('professional.services._form',['action'=>$service->exists?route('admin.professionals.services.update',[$professional,$service]):route('admin.professionals.services.store',$professional),'method'=>$service->exists?'PUT':null,'submitLabel'=>$service->exists?'Salvar serviço':'Criar serviço','cancelRoute'=>route('admin.professionals.show',$professional),'locationCreateRoute'=>route('admin.professionals.locations.create',$professional)])
@endsection
