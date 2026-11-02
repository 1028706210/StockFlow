export const canManageAccounts=me=>!!me?.roles?.includes('ROLE_ADMIN');
export function validatePasswordChange(currentPassword,newPassword,confirmation){
  if(!currentPassword)throw new Error('请输入当前密码。');
  if(!newPassword.trim()||[...newPassword].length<8||[...newPassword].length>64||new TextEncoder().encode(newPassword).length>72)throw new Error('新密码需为 8–64 个字符，不能全为空白，UTF-8 编码不超过 72 字节。');
  if(newPassword!==confirmation)throw new Error('两次输入的新密码不一致。');
  if(currentPassword===newPassword)throw new Error('新密码不能与当前密码相同。');
  return {currentPassword,newPassword};
}
export function createAccountSecurity({document,getSession,request,ui,onPasswordChanged}){
  const {$,node,button,cell,message,failure}=ui;
  let sequence=0,query={q:'',page:1,size:8},pending=null,changing=false;
  const fields=['#password-current','#password-new','#password-confirm'];
  function clearPasswords(){for(const selector of fields)$(selector).value='';}
  $('#password-dialog').addEventListener('close',clearPasswords);
  $('#open-account-security').addEventListener('click',()=>{clearPasswords();message('#password-error');$('#password-dialog').showModal();$('#password-current').focus();});
  $('#password-form').addEventListener('submit',async event=>{
    event.preventDefault();if(changing)return;
    const epoch=getSession().epoch;
    try{
      const body=validatePasswordChange(...fields.map(selector=>$(selector).value));
      changing=true;$('#password-submit').disabled=true;message('#password-error');
      await request('POST','/api/auth/password',body);
      if(epoch!==getSession().epoch)return;
      clearPasswords();$('#password-dialog').close();onPasswordChanged();
    }catch(error){if(!error.stale&&epoch===getSession().epoch)message('#password-error',failure(error));}
    finally{if(epoch===getSession().epoch){changing=false;$('#password-submit').disabled=false;}}
  });
  async function load(){
    if(!canManageAccounts(getSession().me))return;
    const epoch=getSession().epoch,current=++sequence;
    $('#accounts-body').replaceChildren();$('#accounts-controls').replaceChildren();message('#accounts-note','正在加载账号…');
    try{
      const params=new URLSearchParams(query),{data}=await request('GET','/api/admin/accounts?'+params);
      if(epoch!==getSession().epoch||current!==sequence)return;
      query.page=data.page;const body=$('#accounts-body');
      for(const item of data.items){
        const row=node('tr');cell(row,item.username);cell(row,{BUYER:'买家',WAREHOUSE:'仓管',ADMIN:'管理员'}[item.role]||item.role);cell(row,item.enabled?'已启用':'已停用');
        const action=button(item.enabled?'停用':'启用',()=>openStatus(item));action.disabled=item.id===getSession().me.id||(item.role==='ADMIN'&&item.enabled);cell(row,action,'right');body.append(row);
      }
      message('#accounts-note',data.total?'共 '+data.total+' 个账号':'没有匹配的账号。');
      const previous=button('上一页',()=>{query.page--;load();}),next=button('下一页',()=>{query.page++;load();});
      previous.disabled=data.page<=1;next.disabled=data.page>=data.totalPages;
      $('#accounts-controls').append(previous,node('span',data.page+' / '+data.totalPages),next);
    }catch(error){if(!error.stale&&epoch===getSession().epoch&&current===sequence){message('#accounts-note',failure(error));$('#accounts-controls').append(button('重新加载',load));}}
  }
  function openStatus(item){
    if($('#account-command-form').dataset.busy==='true')return;
    pending={id:item.id,enabled:!item.enabled};message('#account-command-error');
    $('#account-command-title').textContent=item.enabled?'停用账号':'启用账号';
    $('#account-command-description').textContent=item.username+'：'+(item.enabled?'停用后不能登录，已有会话将在下次请求时失效。订单与历史记录保留。':'启用后需要重新登录，旧会话不会恢复。');
    $('#account-command-submit').textContent=item.enabled?'确认停用':'确认启用';$('#account-command-dialog').showModal();
  }
  $('#account-command-form').addEventListener('submit',async event=>{
    event.preventDefault();const form=$('#account-command-form');if(form.dataset.busy==='true'||!pending)return;
    const epoch=getSession().epoch,attempt=pending;form.dataset.busy='true';$('#account-command-submit').disabled=true;message('#account-command-error');
    try{
      await request('PUT','/api/admin/accounts/'+attempt.id+'/enabled',{enabled:attempt.enabled});
      if(epoch!==getSession().epoch)return;
      pending=null;$('#account-command-dialog').close();await load();
    }catch(error){if(!error.stale&&epoch===getSession().epoch)message('#account-command-error',failure(error));}
    finally{if(epoch===getSession().epoch){form.dataset.busy='false';$('#account-command-submit').disabled=false;}}
  });
  $('#accounts-filter').addEventListener('submit',event=>{event.preventDefault();query={...query,q:$('#accounts-search').value.trim().slice(0,32),page:1};load();});
  function reset(){sequence++;pending=null;changing=false;query={q:'',page:1,size:8};clearPasswords();$('#password-submit').disabled=false;$('#account-command-submit').disabled=false;$('#account-command-form').dataset.busy='false';$('#accounts-body').replaceChildren();$('#accounts-controls').replaceChildren();$('#accounts-search').value='';}
  return {reset,show:()=>load(),refresh:load};
}
