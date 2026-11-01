// 原创路径商品示意图，仅用于公开的固定演示商品，不代表实物照片。
import {mkdir,writeFile} from 'node:fs/promises';
const drawings={
  cup:'<path d="M180 100h105l-10 152q-44 22-85 0z" fill="#ece8da"/><path d="M180 93q52-18 105 0v18H180z" fill="#9bbbaa"/><path d="M285 125h20q45 40-8 73h-18" fill="none" stroke="#ece8da" stroke-width="14"/><path d="M204 130v95" stroke="#fff" opacity=".6" stroke-width="7"/>',
  note:'<path d="M155 70h165v196H155z" fill="#ede6d3"/><path d="M155 70h18v196h-18z" fill="#91ad98"/><rect x="191" y="100" width="99" height="44" rx="3" fill="#d2cbb7"/><path d="M204 120h73M204 130h45" stroke="#7a8276" stroke-width="3"/><path d="M195 170h94m-94 20h94m-94 20h94m-94 20h94" stroke="#b2b7a0" stroke-dasharray="2 10" stroke-width="3"/>',
  pen:'<g transform="rotate(30 240 166)"><rect x="226" y="69" width="28" height="167" rx="9" fill="#9ab6a0"/><path d="M226 224h28l-14 31z" fill="#e4decc"/><path d="M238 250h4l-2 8z" fill="#293e37"/><path d="M232 57h16v21h-16z" fill="#c9d7c8"/><path d="M254 93h9v77" stroke="#dfe8df" stroke-width="5" fill="none"/></g>',
  bag:'<path d="M151 122h178l-13 142H164z" fill="#ddd0b0"/><path d="M191 132v-38q0-48 49-48t49 48v38" fill="none" stroke="#c9b992" stroke-width="13"/><path d="M176 143l8 98m121-98-8 98" stroke="#b4a785" stroke-width="3"/><path d="M217 178h46v47h-46z" fill="#97a58b"/>',
  box:'<path d="M133 127l110-37 110 37-110 41z" fill="#d8be92"/><path d="M133 127l110 41v99l-110-43z" fill="#b48c5e"/><path d="M243 168l110-41v97l-110 43z" fill="#caab78"/><path d="M243 101v54m-98-24 99 38 97-37" fill="none" stroke="#f0ddba" stroke-width="5"/><path d="M276 196l44-17v15l-44 17z" fill="#806c4b"/>',
  cable:'<path d="M197 105c-87 0-85 135 12 135h52c96 0 95-135 6-135" stroke="#a6b2b5" stroke-width="17" fill="none"/><path d="M197 105v-22m70 22V83" stroke="#dce6df" stroke-width="13"/><rect x="183" y="65" width="28" height="37" rx="7" fill="#cbd5cb"/><rect x="254" y="65" width="26" height="37" rx="7" fill="#cbd5cb"/><path d="M190 53h14v16h-14zm70 0h14v16h-14z" fill="#81908a"/><path d="M218 240h37" stroke="#546f62" stroke-width="24"/>',
  bottle:'<path d="M214 58h52v31l16 25v133q-43 25-84 0V114l16-25z" fill="#9eb29a"/><rect x="207" y="54" width="66" height="32" rx="8" fill="#d3d6bc"/><path d="M214 126v102" stroke="#dfe9cc" stroke-width="7"/><path d="M198 163h84v51h-84z" fill="#6c8f77"/><path d="M224 179h32m-32 11h23" stroke="#c9deba" stroke-width="3"/>',
  pouch:'<rect x="129" y="114" width="222" height="133" rx="25" fill="#8c9998"/><path d="M151 137h177" stroke="#d8ddd0" stroke-width="6"/><rect x="297" y="130" width="15" height="29" rx="5" fill="#e3d7b8"/><path d="M153 176h174v46H153z" fill="#657975"/><path d="M180 197h120" stroke="#b8c8b6" stroke-dasharray="2 7" stroke-width="2"/>'
};
const target=new URL('../../src/main/resources/static/assets/ui/products/',import.meta.url);await mkdir(target,{recursive:true});
for(const [name,paths]of Object.entries(drawings)){
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 320"><title>StockFlow 原创商品示意：${name}</title><rect width="480" height="320" rx="12" fill="#17312e"/><circle cx="380" cy="47" r="106" fill="#26413a"/><path d="M45 275h390" stroke="#466052"/><ellipse cx="240" cy="271" rx="119" ry="10" fill="#0d231e"/>${paths}</svg>\n`;
  await writeFile(new URL(name+'.svg',target),svg);
}
