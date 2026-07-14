<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\AttendanceLocation;
use App\Models\AvailabilityBlock;
use App\Models\ProfessionalAvailability;
use App\Models\Room;
use App\Models\Service;
use App\Models\ServiceCategory;
use App\Models\User;
use App\Support\ImageOptimizer;
use Carbon\CarbonImmutable;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\View\View;

class ProfessionalWorkspaceController extends Controller
{
    public function createService(User $professional): View { $this->professional($professional); return $this->serviceForm($professional,new Service(['modality'=>'presencial','duration_minutes'=>60,'buffer_minutes'=>0,'max_installments'=>1,'requires_prepayment'=>true,'is_active'=>true])); }
    public function editService(User $professional,Service $service): View { $this->owns($professional,$service->professional_id);return $this->serviceForm($professional,$service); }
    public function storeService(Request $request,User $professional): RedirectResponse { $this->professional($professional);$data=$this->serviceData($request);$service=Service::create($data+['professional_id'=>$professional->id,'slug'=>$this->uniqueSlug($data['title']),'professional_name'=>$professional->display_name,'professional_city'=>$professional->city,'professional_state'=>$professional->state,'cover_path'=>$this->cover($request)]);$service->locations()->sync($this->locationIds($request,$professional));return redirect()->route('admin.professionals.show',$professional)->with('status','Serviço criado.'); }
    public function updateService(Request $request,User $professional,Service $service): RedirectResponse { $this->owns($professional,$service->professional_id);$data=$this->serviceData($request);if($cover=$this->cover($request))$data['cover_path']=$cover;$service->update($data);$service->locations()->sync($this->locationIds($request,$professional));return redirect()->route('admin.professionals.show',$professional)->with('status','Serviço atualizado.'); }
    public function deleteService(User $professional,Service $service): RedirectResponse { $this->owns($professional,$service->professional_id);$service->delete();return back()->with('status','Serviço removido.'); }

    public function createLocation(User $professional): View { $this->professional($professional);return view('admin.professional-location-form',['professional'=>$professional,'location'=>new AttendanceLocation(['is_active'=>true]),'rooms'=>Room::active()->orderBy('position')->get()]); }
    public function editLocation(User $professional,AttendanceLocation $location): View { $this->owns($professional,$location->professional_id);return view('admin.professional-location-form',['professional'=>$professional,'location'=>$location,'rooms'=>Room::active()->orderBy('position')->get()]); }
    public function storeLocation(Request $request,User $professional): RedirectResponse { $this->professional($professional);$professional->attendanceLocations()->create($this->locationData($request));return redirect()->route('admin.professionals.show',$professional)->with('status','Local criado.'); }
    public function updateLocation(Request $request,User $professional,AttendanceLocation $location): RedirectResponse { $this->owns($professional,$location->professional_id);$location->update($this->locationData($request));return redirect()->route('admin.professionals.show',$professional)->with('status','Local atualizado.'); }
    public function deleteLocation(User $professional,AttendanceLocation $location): RedirectResponse { $this->owns($professional,$location->professional_id);$location->delete();return back()->with('status','Local removido.'); }

    public function availability(User $professional): View { $this->professional($professional);return view('admin.professional-availability',['professional'=>$professional,'locations'=>$professional->attendanceLocations()->where('is_active',true)->orderBy('name')->get(),'availabilities'=>$professional->availabilities()->orderBy('weekday')->orderBy('start_time')->get(),'blocks'=>$professional->availabilityBlocks()->with('location')->where('ends_at','>=',now())->orderBy('starts_at')->get(),'weekdays'=>ProfessionalAvailability::WEEKDAYS]); }
    public function updateAvailability(Request $request,User $professional): RedirectResponse { $this->professional($professional);$data=$request->validate(['rows'=>['array'],'rows.*.attendance_location_id'=>['required','integer'],'rows.*.weekday'=>['required','integer','between:0,6'],'rows.*.start_time'=>['required','date_format:H:i'],'rows.*.end_time'=>['required','date_format:H:i']]);$owned=$professional->attendanceLocations()->pluck('id')->all();DB::transaction(function()use($professional,$data,$owned){$professional->availabilities()->delete();foreach($data['rows']??[] as $row)if(in_array((int)$row['attendance_location_id'],$owned,true)&&$row['end_time']>$row['start_time'])$professional->availabilities()->create($row+['is_active'=>true]);});return back()->with('status','Disponibilidade atualizada.'); }
    public function storeBlock(Request $request,User $professional): RedirectResponse { $this->professional($professional);$data=$request->validate(['attendance_location_id'=>['nullable','integer'],'date'=>['required','date'],'all_day'=>['nullable','boolean'],'start_time'=>['nullable','date_format:H:i'],'end_time'=>['nullable','date_format:H:i','after:start_time'],'reason'=>['nullable','string','max:255']]);$tz=$professional->timezone?:config('pindorama.timezone');$date=CarbonImmutable::parse($data['date'],$tz);$all=$request->boolean('all_day');$locationId=$data['attendance_location_id']??null;if($locationId&&!$professional->attendanceLocations()->whereKey($locationId)->exists())$locationId=null;AvailabilityBlock::create(['professional_id'=>$professional->id,'attendance_location_id'=>$locationId,'starts_at'=>$all?$date->startOfDay():$date->setTimeFromTimeString($data['start_time']??'00:00'),'ends_at'=>$all?$date->endOfDay():$date->setTimeFromTimeString($data['end_time']??'23:59'),'all_day'=>$all,'reason'=>$data['reason']??null]);return back()->with('status','Bloqueio criado.'); }
    public function deleteBlock(User $professional,AvailabilityBlock $block): RedirectResponse { $this->owns($professional,$block->professional_id);$block->delete();return back()->with('status','Bloqueio removido.'); }

    private function serviceForm(User $professional,Service $service): View { return view('admin.professional-service-form',['professional'=>$professional,'service'=>$service,'categories'=>ServiceCategory::with('children')->roots()->orderBy('position')->get(),'locations'=>$professional->attendanceLocations()->where('is_active',true)->orderBy('name')->get(),'selectedLocations'=>$service->exists?$service->locations()->pluck('attendance_locations.id')->all():[]]); }
    private function serviceData(Request $request): array { $data=$request->validate(['service_category_id'=>['required','exists:service_categories,id'],'title'=>['required','string','max:255'],'description'=>['nullable','string'],'modality'=>['required','in:presencial,online,ambos'],'duration_minutes'=>['required','integer','min:5','max:480'],'buffer_minutes'=>['nullable','integer','min:0','max:120'],'price'=>['required','numeric','min:0'],'compare_at_price'=>['nullable','numeric','min:0'],'max_installments'=>['required','integer','min:1','max:12'],'requires_prepayment'=>['nullable','boolean'],'is_active'=>['nullable','boolean'],'cover'=>['nullable','image','max:8192']]);unset($data['cover']);$data['requires_prepayment']=$request->boolean('requires_prepayment');$data['is_active']=$request->boolean('is_active');$data['buffer_minutes']=(int)($data['buffer_minutes']??0);return $data; }
    private function locationData(Request $request): array { $data=$request->validate(['room_id'=>['nullable','exists:rooms,id'],'name'=>['required','string','max:255'],'is_online'=>['nullable','boolean'],'address'=>['nullable','string','max:255'],'neighborhood'=>['nullable','string','max:120'],'city'=>['nullable','string','max:120'],'state'=>['nullable','string','max:60'],'zip'=>['nullable','string','max:20'],'complement'=>['nullable','string','max:255'],'map_url'=>['nullable','url','max:500'],'is_active'=>['nullable','boolean'],'position'=>['nullable','integer','min:0']]);$data['is_online']=$request->boolean('is_online');$data['is_active']=$request->boolean('is_active');$data['position']=(int)($data['position']??0);return $data; }
    private function locationIds(Request $request,User $professional): array { return array_values(array_intersect(array_map('intval',(array)$request->input('locations',[])),$professional->attendanceLocations()->pluck('id')->all())); }
    private function cover(Request $request): ?string { $file=$request->file('cover');return $file&&$file->isValid()?'/storage/'.ImageOptimizer::store($file,'services/covers'):null; }
    private function uniqueSlug(string $title): string {$base=Str::slug($title)?:'servico';$slug=$base;$i=2;while(Service::where('slug',$slug)->exists())$slug=$base.'-'.$i++;return $slug;}
    private function professional(User $professional): void { abort_unless($professional->is_professional&&!$professional->isRoot(),404); }
    private function owns(User $professional,int $owner): void {$this->professional($professional);abort_unless($professional->id===$owner,404);}
}
