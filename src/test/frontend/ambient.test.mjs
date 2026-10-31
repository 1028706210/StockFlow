import test from 'node:test';
import assert from 'node:assert/strict';
import {initAmbient} from '../../main/resources/static/assets/ui/ambient.mjs';

function fixture({saved='',reduced=false,noContext=false,density='standard',kind='overview',width=800,height=190,page='overview'}={}){
  const listeners=new Map(),frames=new Map(),storage=new Map([['stockflow.motion',saved],['stockflow.density',density]]);
  const eventTarget=()=>({addEventListener(type,fn){listeners.set(this.name+type,fn);},removeEventListener(type){listeners.delete(this.name+type);}});
  const host={name:'host',...eventTarget()},workspace={dataset:{page}};let draws=0,id=0,io;const points=[],colors=[];let strokes=0;
  const ctx={clearRect(){draws++;points.length=0;colors.length=0;strokes=0;},beginPath(){},arc(x,y,r){if(r<3)points.push({x,y,r});},fill(){colors.push(this.fillStyle);},setTransform(){},moveTo(){},lineTo(){},stroke(){strokes++;}};
  const canvas={name:'canvas',dataset:{particleField:kind},getContext:()=>noContext?null:ctx,getBoundingClientRect:()=>({width,height,left:0,top:0}),parentElement:host};
  const copy={},attributes={};const button={name:'button',...eventTarget(),querySelector:()=>copy,setAttribute:(key,value)=>attributes[key]=value};
  const media={name:'media',...eventTarget(),matches:reduced};const fine={matches:true};
  const densityControl={name:'density',value:density,...eventTarget()};
  const root={name:'document',...eventTarget(),body:{dataset:{}},hidden:false,querySelector:()=>workspace,querySelectorAll:selector=>selector==='[data-particle-field]'?[canvas]:selector==='[data-motion-toggle]'?[button]:selector==='[data-particle-density]'?[densityControl]:[]};
  const env={name:'window',...eventTarget(),devicePixelRatio:2,matchMedia:query=>query.includes('reduced')?media:fine,localStorage:{getItem:key=>storage.get(key),setItem:(key,value)=>storage.set(key,value)},requestAnimationFrame:fn=>{frames.set(++id,fn);return id;},cancelAnimationFrame:id=>frames.delete(id),ResizeObserver:class{constructor(fn){this.fn=fn;}observe(){}disconnect(){}},IntersectionObserver:class{constructor(fn){io=fn;}observe(){}disconnect(){}}};
  const dispose=initAmbient(root,env);
  return {root,env,media,button,densityControl,attributes,storage,frames,dispose,points,colors,workspace,strokes:()=>strokes,draws:()=>draws,visible(value=true){io([{target:canvas,isIntersecting:value}]);},event(name,event){listeners.get(name)?.(event);},advance(time){const [key,fn]=frames.entries().next().value;frames.delete(key);fn(time);},listenerCount:()=>listeners.size};
}
test('粒子仅在可见且页面前台时调度，离屏与后台立即停止',()=>{
 const f=fixture();assert.equal(f.frames.size,0);f.visible();assert.equal(f.frames.size,1);f.advance(100);assert.equal(f.frames.size,1);
 f.root.hidden=true;f.event('documentvisibilitychange');assert.equal(f.frames.size,0);
 f.root.hidden=false;f.event('documentvisibilitychange');assert.equal(f.frames.size,1);f.visible(false);assert.equal(f.frames.size,0);f.dispose();
});
test('系统减少动态效果保留静态粒子但不执行动画，并响应设置改变',()=>{
 const f=fixture({reduced:true});f.visible();assert.equal(f.frames.size,0);assert.equal(f.root.body.dataset.motion,'reduced');assert.equal(f.button.disabled,true);assert.ok(f.draws()>0);
 f.media.matches=false;f.event('mediachange');assert.equal(f.frames.size,1);assert.equal(f.button.disabled,false);f.dispose();
});
test('暂停控制同步所有状态并保存，恢复后重新调度',()=>{
 const f=fixture();f.visible();f.event('buttonclick');assert.equal(f.frames.size,0);assert.equal(f.attributes['aria-pressed'],'true');assert.equal(f.storage.get('stockflow.motion'),'paused');assert.equal(f.root.body.dataset.motion,'paused');
 f.event('buttonclick');assert.equal(f.frames.size,1);assert.equal(f.attributes['aria-pressed'],'false');assert.equal(f.storage.get('stockflow.motion'),'running');f.dispose();
});
test('重新打开页面保留暂停偏好，销毁后移除监听和动画',()=>{
 const f=fixture({saved:'paused'});f.visible();assert.equal(f.frames.size,0);assert.equal(f.root.body.dataset.motion,'paused');f.dispose();assert.equal(f.frames.size,0);assert.equal(f.listenerCount(),0);
});
test('不支持Canvas时隐藏装饰控制，不阻断业务页面',()=>{
 const f=fixture({noContext:true});assert.equal(f.button.hidden,true);assert.equal(f.frames.size,0);f.dispose();
});

test('所有粒子类型覆盖左右上下区域，不再只集中在右下角',()=>{
 for(const kind of ['workspace','surface','login','overview']){
  const f=fixture({kind,width:900,height:600});f.visible();
  const quadrants=[0,0,0,0];for(const p of f.points)quadrants[(p.x>=450?1:0)+(p.y>=300?2:0)]++;
  for(const count of quadrants)assert.ok(count>f.points.length*.12,kind+' must populate each quadrant');
  assert.ok(f.points.some(p=>p.x<90&&p.y<120),kind+' needs upper-left particles');
  assert.ok(f.points.some(p=>p.x>810&&p.y>480),kind+' needs lower-right particles');f.dispose();
 }
});
test('工作台粒子切换页面时使用对应色调，保留一个调度循环',()=>{
 const f=fixture({kind:'workspace',page:'inventory'});f.visible();f.advance(100);const before=f.colors.join();
 f.workspace.dataset.page='alerts';f.advance(200);assert.notEqual(f.colors.join(),before);assert.equal(f.frames.size,1);f.dispose();
});
test('背景和内容区域持续移动，暂停时全区域恢复静态',()=>{
 for(const kind of ['workspace','surface']){
  const f=fixture({kind});f.visible();const initial=f.points.map(p=>[p.x,p.y]);f.advance(1000);assert.notDeepEqual(f.points.map(p=>[p.x,p.y]),initial);
  f.event('buttonclick');assert.equal(f.frames.size,0);assert.deepEqual(f.points.map(p=>[p.x,p.y]),initial);f.dispose();
 }
});
test('鼠标进入内容区时产生有限连线，离开后清除交互',()=>{
 const f=fixture({kind:'surface'});f.visible();const point=f.points[20];f.event('hostpointermove',{clientX:point.x,clientY:point.y});f.advance(100);
 assert.ok(f.strokes()>0);assert.ok(f.strokes()<=7);f.event('hostpointerleave');f.advance(200);assert.equal(f.strokes(),0);f.dispose();
});
test('手机全屏粒子限制数量并覆盖整个页面',()=>{
 const f=fixture({kind:'workspace',width:390,height:844});f.visible();assert.ok(f.points.length<=200);assert.ok(f.points.some(p=>p.y<168));assert.ok(f.points.some(p=>p.y>675));f.dispose();
});

test('粒子密度可降低到四成，关闭后不再绘制或调度，并保存选择',()=>{const normal=fixture();normal.visible();const n=normal.points.length;normal.dispose();const f=fixture({density:'low'});f.visible();assert.ok(f.points.length<n*.5);f.densityControl.value='off';f.event('densitychange');assert.equal(f.points.length,0);assert.equal(f.frames.size,0);assert.equal(f.storage.get('stockflow.density'),'off');f.densityControl.value='standard';f.event('densitychange');assert.equal(f.frames.size,1);f.dispose();});
