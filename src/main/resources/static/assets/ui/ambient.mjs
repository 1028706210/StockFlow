/** Local decorative layers: viewport dust, flowing streams and pointer connections. */
export function initAmbient(root=document,env=window){
  const canvases=[...root.querySelectorAll('[data-particle-field]')];
  if(!canvases.length)return ()=>{};
  const media=env.matchMedia('(prefers-reduced-motion: reduce)'),fine=env.matchMedia('(pointer: fine)');
  const toggles=[...root.querySelectorAll('[data-motion-toggle]')],workspace=root.querySelector?.('#workspace');
  const palettes={
    overview:['139,239,200','125,205,235','235,193,133'],
    products:['139,239,200','125,205,235','235,193,133'],
    inventory:['118,228,204','122,207,240','170,235,205'],
    ledger:['129,205,243','178,169,240','131,230,207'],
    alerts:['239,194,127','138,229,196','140,204,226'],
    fulfillment:['126,226,209','236,190,139','129,203,240'],
    orders:['139,207,244','176,178,241','133,233,205'],
    'new-order':['141,238,203','128,206,241','188,205,242']
  };
  const densityControls=[...root.querySelectorAll('[data-particle-density]')];
  let density='standard';try{const saved=env.localStorage.getItem('stockflow.density');if(['low','off'].includes(saved))density=saved;}catch{}
  let paused=false,frame=0,last=-Infinity,disposed=false;
  try{paused=env.localStorage.getItem('stockflow.motion')==='paused';}catch{}
  const fields=canvases.map(canvas=>{
    const ctx=canvas.getContext('2d',{alpha:true});if(!ctx)return null;
    return {canvas,ctx,width:0,height:0,visible:false,mouse:{x:-10000,y:-10000}};
  }).filter(Boolean);
  if(!fields.length){toggles.forEach(button=>button.hidden=true);return ()=>{};}
  const unit=value=>value-Math.floor(value);
  function draw(field,clock=0){
    const {ctx,width:w,height:h,canvas,mouse}=field;if(!w||!h)return;
    ctx.clearRect(0,0,w,h);
    const kind=canvas.dataset.particleField,surface=kind==='surface',global=kind==='workspace',small=w<600;
    if(density==='off')return;
    const baseCount=surface?(small?85:165):global?(small?175:360):kind==='login'?(small?230:860):(small?280:640);
    const count=Math.ceil(baseCount*(density==='low'?.4:1));
    const view=canvas.closest?.('.view')?.id?.slice(5)||workspace?.dataset.page||'overview';
    const palette=palettes[view]||palettes.overview,phase=clock*.00009;
    let connections=0;
    for(let i=0;i<count;i++){
      const t=unit(i*.61803398875),seed=unit(Math.sin(i*12.9898+1)*43758.5453),second=unit(Math.sin(i*47.13+4)*12749.217);
      const dust=global?i%4!==0:i%3===0;
      let x,y;
      if(dust){
        x=unit(t+clock*(.000002+seed*.000003))*w;
        y=unit(second+Math.sin(phase*.55+i)*.025-clock*(.000001+seed*.000001))*h;
      }else{
        const band=Math.floor(i/3)%3;
        x=unit(t+Math.sin(phase*.25+i)*.006)*w;
        y=h*((band+.5)/3)+Math.sin(t*6.3+phase+band*1.6)*h*.085+(second-.5)*h*.14;
        y=Math.min(h-2,Math.max(2,y));
      }
      const dx=x-mouse.x,dy=y-mouse.y,distance=Math.hypot(dx,dy);
      if(fine.matches&&distance>0&&distance<115){const force=(1-distance/115)*(surface?7:16);x+=dx/distance*force;y+=dy/distance*force;}
      const shimmer=.72+.28*Math.sin(phase*1.4+i*.73);
      const alpha=(surface?.16:global?.26:.24)+seed*(surface?.34:.5);
      const color=palette[i%19===0?2:i%5===0?1:0];
      const radius=(surface?.45:.55)+seed*(i%23===0?1.5:.95);
      ctx.fillStyle='rgba('+color+','+alpha*shimmer+')';ctx.beginPath();ctx.arc(x,y,radius,0,Math.PI*2);ctx.fill();
      if(i%31===0){ctx.fillStyle='rgba('+color+','+alpha*.1+')';ctx.beginPath();ctx.arc(x,y,surface?4:7,0,Math.PI*2);ctx.fill();}
      if(fine.matches&&distance<100&&connections<7){
        ctx.strokeStyle='rgba('+color+','+(1-distance/100)*.26+')';ctx.lineWidth=.6;ctx.beginPath();ctx.moveTo(mouse.x,mouse.y);ctx.lineTo(x,y);ctx.stroke();connections++;
      }
    }
  }
  function resize(field){
    const rect=field.canvas.getBoundingClientRect(),ratio=Math.min(env.devicePixelRatio||1,1.5);
    field.width=rect.width;field.height=rect.height;
    field.canvas.width=Math.round(rect.width*ratio);field.canvas.height=Math.round(rect.height*ratio);field.ctx.setTransform(ratio,0,0,ratio,0,0);draw(field);
  }
  function tick(now){
    frame=0;if(disposed||density==='off'||paused||media.matches||root.hidden)return;
    if(now-last>=42){fields.filter(field=>field.visible).forEach(field=>draw(field,now));last=now;}
    if(fields.some(field=>field.visible))frame=env.requestAnimationFrame(tick);
  }
  function reconcile(){
    if(disposed)return;if(frame){env.cancelAnimationFrame(frame);frame=0;}
    root.body.dataset.density=density;for(const control of densityControls)control.value=density;
    const reduced=media.matches;root.body.dataset.motion=reduced?'reduced':paused?'paused':'running';
    for(const button of toggles){button.disabled=reduced;button.setAttribute('aria-pressed',String(paused||reduced));button.setAttribute('aria-label',reduced?'已遵循减少动态效果设置':paused?'开启背景动效':'暂停背景动效');button.title=reduced?'已遵循系统减少动态效果设置':paused?'开启背景动效':'暂停背景动效';const copy=button.querySelector('span');if(copy)copy.textContent=reduced?'减少动态':paused?'动效已暂停':'动态背景';}
    if(density==='off'){fields.forEach(field=>field.ctx.clearRect(0,0,field.width,field.height));return;}
    if(reduced||paused){fields.filter(field=>field.visible).forEach(field=>draw(field));return;}
    if(!root.hidden&&fields.some(field=>field.visible))frame=env.requestAnimationFrame(tick);
  }
  const resizeObserver=env.ResizeObserver?new env.ResizeObserver(entries=>{for(const entry of entries){const field=fields.find(field=>field.canvas===entry.target);if(field)resize(field);}reconcile();}):null;
  const visibilityObserver=env.IntersectionObserver?new env.IntersectionObserver(entries=>{for(const entry of entries){const field=fields.find(field=>field.canvas===entry.target);if(field){field.visible=entry.isIntersecting;if(field.visible)resize(field);}}reconcile();}):null;
  const cleanup=[];
  function listen(target,event,handler){target.addEventListener(event,handler);cleanup.push(()=>target.removeEventListener(event,handler));}
  for(const field of fields){
    resize(field);resizeObserver?.observe(field.canvas);visibilityObserver?.observe(field.canvas);if(!visibilityObserver)field.visible=true;
    const host=field.canvas.parentElement;
    listen(host,'pointermove',event=>{if(!fine.matches||paused||media.matches)return;const rect=field.canvas.getBoundingClientRect();field.mouse.x=event.clientX-rect.left;field.mouse.y=event.clientY-rect.top;});
    listen(host,'pointerleave',()=>{field.mouse.x=-10000;field.mouse.y=-10000;});
  }
  for(const control of densityControls)listen(control,'change',()=>{if(!['standard','low','off'].includes(control.value))return;density=control.value;try{env.localStorage.setItem('stockflow.density',density);}catch{}fields.filter(field=>field.visible).forEach(field=>draw(field));reconcile();});
  for(const button of toggles)listen(button,'click',()=>{paused=!paused;try{env.localStorage.setItem('stockflow.motion',paused?'paused':'running');}catch{}reconcile();});
  listen(root,'visibilitychange',reconcile);listen(media,'change',reconcile);if(!resizeObserver)listen(env,'resize',()=>{fields.forEach(resize);reconcile();});
  reconcile();return ()=>{disposed=true;if(frame)env.cancelAnimationFrame(frame);resizeObserver?.disconnect();visibilityObserver?.disconnect();cleanup.forEach(fn=>fn());};
}
if(typeof document!=='undefined'&&typeof window!=='undefined'){
  try{initAmbient();}catch{ /* Optional decoration must never block the business UI. */ }
}
