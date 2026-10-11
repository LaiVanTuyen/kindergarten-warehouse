const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const env = Object.fromEntries(fs.readFileSync(path.join(root,'.env.demo'),'utf8').split(/\r?\n/).filter(l=>/^[A-Z0-9_]+=/.test(l)).map(l=>{const i=l.indexOf('=');return [l.slice(0,i),l.slice(i+1).replace(/^['"]|['"]$/g,'')];}));
const base = 'http://localhost:4301/api/v1';
const results=[];
class Client {
  cookies = new Map();
  async request(route, method='GET', body, expected=200) {
    const headers={Origin:'http://localhost:4301'};
    if(this.cookies.size)headers.Cookie=[...this.cookies].map(([k,v])=>k+'='+v).join('; ');
    if(method!=='GET'&&this.cookies.has('XSRF-TOKEN'))headers['X-XSRF-TOKEN']=decodeURIComponent(this.cookies.get('XSRF-TOKEN'));
    if(body&&!(body instanceof FormData)){headers['Content-Type']='application/json';body=JSON.stringify(body);}
    const response=await fetch(base+route,{method,headers,body,signal:AbortSignal.timeout(20000)});
    for(const cookie of response.headers.getSetCookie()){const pair=cookie.split(';')[0],i=pair.indexOf('=');this.cookies.set(pair.slice(0,i),pair.slice(i+1));}
    const type=response.headers.get('content-type')||'';
    const data=type.includes('json')?await response.json():Buffer.from(await response.arrayBuffer());
    results.push({route,method,status:response.status,expected,pass:response.status===expected});
    assert.equal(response.status,expected,method+' '+route+': '+JSON.stringify(data).slice(0,300));
    return data.result ?? data;
  }
  async login(email,password){await this.request('/auth/login','POST',{email,password});return this.request('/users/me');}
}
const admin=new Client(),teacher=new Client(),guest=new Client();
async function upload(client,title){
  const form=new FormData();form.set('title',title);form.set('description','Tài liệu minh họa cho bản demo, không chứa dữ liệu thật của trẻ.');form.set('topicId',String(global.topic.id));
  const ages=await guest.request('/age-groups');if(ages.length)form.append('ageGroupIds',String(ages[0].id));
  form.set('file',new Blob([fs.readFileSync(path.join(root,'target/demo-preview.pdf'))],{type:'application/pdf'}),'demo-preview.pdf');
  return client.request('/resources','POST',form,201);
}
(async()=>{
  await admin.login(env.APP_ADMIN_EMAIL,env.APP_ADMIN_PASSWORD);
  const users=await admin.request('/users?size=100');
  let demo=users.content.find(u=>u.username==='demo_teacher');
  if(!demo)demo=await admin.request('/users','POST',{username:'demo_teacher',email:'demo.teacher@example.com',password:'<redacted>',fullName:'Giáo viên Demo',roles:['TEACHER']},201);
  await teacher.login('demo.teacher@example.com','<redacted>');
  global.topic=(await guest.request('/topics?size=100')).content.find(t=>t.visibility==='PUBLIC');assert.ok(global.topic,'Public topic missing');
  const uploaded=await upload(teacher,'[DEMO] Học qua trò chơi — giáo viên gửi duyệt');assert.equal(uploaded.status,'PENDING');
  await guest.request('/resources/'+uploaded.slug,'GET',undefined,404);
  await teacher.request('/resources/me');
  const approved=await admin.request('/admin/resources/'+uploaded.id+'/approve','PATCH');assert.equal(approved.status,'APPROVED');
  await guest.request('/resources?keyword='+encodeURIComponent('[DEMO]'));
  const before=await guest.request('/resources/'+approved.slug);
  let preview;
  for(let i=0;i<60;i++){preview=await guest.request('/resources/'+approved.id+'/preview');if(preview.status!=='PROCESSING')break;await new Promise(r=>setTimeout(r,1500));}
  assert.equal(preview.status,'READY');assert.equal(preview.pageCount,3);
  await guest.request('/resources/'+approved.id+'/preview/pages/1');
  await guest.request('/resources/'+approved.id+'/file','GET',undefined,401);
  assert.equal((await guest.request('/resources/'+approved.slug)).downloadCount,before.downloadCount);
  const file=await teacher.request('/resources/'+approved.id+'/file');assert.ok(file.length>100);
  assert.equal((await guest.request('/resources/'+approved.slug)).downloadCount,before.downloadCount+1);
  await teacher.request('/resources/'+approved.id+'/favorite','POST',{});
  assert.ok((await teacher.request('/favorites/ids')).includes(approved.id));
  assert.ok((await teacher.request('/favorites')).content.some(r=>r.id===approved.id));
  await teacher.request('/comments?resourceId='+approved.id+'&rating=5&content='+encodeURIComponent('Học liệu demo dễ sử dụng cho hoạt động trong lớp.'),'POST',undefined,201);
  assert.ok((await guest.request('/comments?resourceId='+approved.id)).content.length);
  await admin.request('/resources/'+approved.id+'/visibility','PATCH',{visibility:'PRIVATE'});
  await guest.request('/resources/'+approved.id+'/preview','GET',undefined,404);
  await guest.request('/resources/'+approved.id+'/preview/pages/1','GET',undefined,404);
  await teacher.request('/resources/'+approved.id+'/preview/pages/1');
  await admin.request('/resources/'+approved.id+'/visibility','PATCH',{visibility:'PUBLIC'});
  const rejected=await upload(teacher,'[DEMO] Tài liệu cần bổ sung — mẫu từ chối');
  await admin.request('/admin/resources/'+rejected.id+'/reject','PATCH',{reason:'Bổ sung hướng dẫn tổ chức hoạt động trước khi xuất bản.'});
  await guest.request('/resources/'+rejected.slug,'GET',undefined,404);
  for(const route of ['/users?size=10','/categories?size=100','/topics?size=100','/banners/all','/admin/resources?size=20','/audit-logs?size=20'])await admin.request(route);
  const stamp=Date.now();
  const categoryForm=new FormData();categoryForm.set('name','[DEMO] Hoạt động thử nghiệm '+stamp);categoryForm.set('slug','demo-hoat-dong-'+stamp);categoryForm.set('visibility','PUBLIC');
  const category=await admin.request('/categories','POST',categoryForm,201);
  const topic=await admin.request('/topics','POST',{name:'[DEMO] Hoạt động khám phá '+stamp,categoryId:category.id,visibility:'PUBLIC'},201);
  await admin.request('/topics/'+topic.id,'PUT',{name:'[DEMO] Khám phá qua trò chơi '+stamp,categoryId:category.id,visibility:'PUBLIC'});
  await admin.request('/topics/'+topic.id,'DELETE');await admin.request('/topics/'+topic.id+'/restore','PATCH');
  await admin.request('/categories/'+category.id,'PUT',{name:'[DEMO] Hoạt động trải nghiệm '+stamp,slug:category.slug,visibility:'PUBLIC'});
  await admin.request('/categories/'+category.id,'DELETE');await admin.request('/categories/'+category.id+'/restore','PATCH');
  await admin.request('/users/'+demo.id+'/block','PUT',{});await admin.request('/users/'+demo.id+'/block','PUT',{});
  const reviewer=await admin.request('/users','POST',{username:'demo_reviewer_'+Date.now(),email:'demo.reviewer.'+Date.now()+'@example.com',password:'<redacted>',fullName:'Tài khoản thử khôi phục',roles:['USER']},201);
  await admin.request('/users/'+reviewer.id,'DELETE');await admin.request('/users/'+reviewer.id+'/restore','PUT',{});
  const banners=await admin.request('/banners/all');
  if(banners.content.length){await admin.request('/banners/'+banners.content[0].id+'/toggle','PUT');await admin.request('/banners/'+banners.content[0].id+'/toggle','PUT');}
  await admin.request('/resources/'+rejected.id,'DELETE');await admin.request('/resources/'+rejected.id+'/restore','PUT',{});
  await teacher.request('/auth/logout','POST');await teacher.request('/users/me','GET',undefined,401);
  const report={checkedAt:new Date().toISOString(),passed:true,resource:approved,teacher:{email:'demo.teacher@example.com',password:'<redacted>'},preview,results};
  fs.writeFileSync(path.join(root,'target/demo-acceptance.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify({passed:true,checks:results.length,resourceUrl:'http://localhost:4301/resources/'+approved.slug}));
})().catch(error=>{fs.writeFileSync(path.join(root,'target/demo-acceptance.json'),JSON.stringify({passed:false,error:error.message,results},null,2));console.error(error.message);process.exitCode=1;});
