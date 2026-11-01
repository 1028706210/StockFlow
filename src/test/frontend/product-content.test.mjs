import test from 'node:test';
import assert from 'node:assert/strict';
import {productSummary,productFields} from '../../main/resources/static/assets/ui/product-content.mjs';

test('商品资料缺省不编造规格，单位回退为件',()=>{
  assert.equal(productSummary({}), '规格待补充');
  assert.equal(productSummary({category:'办公文具',specification:'A5 / 80页'}),'办公文具 · A5 / 80页');
  const values=new Map([['category',' 办公文具 '],['unit','本'],['specification','A5'],['description','说明']]);
  assert.deepEqual(productFields({get:key=>values.get(key)}),{category:'办公文具',specification:'A5',unit:'本',description:'说明'});
});
