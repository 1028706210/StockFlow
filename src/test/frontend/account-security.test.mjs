import test from 'node:test';
import assert from 'node:assert/strict';
import {validatePasswordChange,canManageAccounts} from '../../main/resources/static/assets/ui/account-security.mjs';
test('改密校验不修剪密码，拒绝短密码、不一致与复用',()=>{
  assert.throws(()=>validatePasswordChange('old-password','short','short'));
  assert.throws(()=>validatePasswordChange('old-password','new-password','different'));
  assert.throws(()=>validatePasswordChange('old-password','old-password','old-password'));
  assert.throws(()=>validatePasswordChange('old-password','界'.repeat(25),'界'.repeat(25)));
  assert.throws(()=>validatePasswordChange('old-password',' '.repeat(8),' '.repeat(8)));
  assert.deepEqual(validatePasswordChange(' old-password ',' new-password ',' new-password '),{currentPassword:' old-password ',newPassword:' new-password '});
});
test('仓管不能获得管理员账号维护入口',()=>{
  assert.equal(canManageAccounts({roles:['ROLE_WAREHOUSE']}),false);
  assert.equal(canManageAccounts({roles:['ROLE_BUYER']}),false);
  assert.equal(canManageAccounts({roles:['ROLE_ADMIN','ROLE_WAREHOUSE']}),true);
});
