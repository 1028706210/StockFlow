"""生成可审查源码包和仅供经营者保管的完整交付包，不包含运行库、密钥或会话。"""
from pathlib import Path
import hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT.parent/'output/序物设计商城交付'
OUT.mkdir(parents=True,exist_ok=True)
def files(source_only):
    for f in sorted(ROOT.rglob('*')):
        if not f.is_file():continue
        rel=f.relative_to(ROOT);parts=rel.parts
        if any(p in ['.git','node_modules','__pycache__','artifacts'] for p in parts):continue
        if f.name=='.env' or f.name.startswith('.env.') and f.name!='.env.example':continue
        if 'sqlite' in f.name or f.name in ['canva-preview-urls.json','test-results.log','frontend-red.log']:continue
        if source_only and (parts[0]=='private' or f.name in ['build-svg-goods.py','build-template-goods.py','record-canva-status.py']):continue
        if parts[:2]==('private','canva-previews'):continue
        yield f,rel
records=[]
for source_only,name in [(True,'序物设计-网站源码.zip'),(False,'序物设计-经营者完整私有交付.zip')]:
    entries=list(files(source_only));dest=OUT/name
    with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=8) as z:
        for f,rel in entries:z.write(f,'序物设计商城/'+rel.as_posix())
    with zipfile.ZipFile(dest) as z:
        assert z.testzip() is None
        assert len(z.namelist())==len(entries)
        assert all('..' not in Path(n).parts for n in z.namelist())
        if source_only:assert not any('/private/' in n or n.endswith('.pptx') or n.endswith('download.zip') for n in z.namelist())
        for f,rel in entries:assert z.read('序物设计商城/'+rel.as_posix())==f.read_bytes()
    records.append({'文件':name,'文件数':len(entries),'字节':dest.stat().st_size,'SHA256':hashlib.sha256(dest.read_bytes()).hexdigest(),'私有经营者包':not source_only})
(OUT/'交付包校验.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
(OUT/'阅读我.txt').write_text('网站源码包不含收费源文件。经营者完整私有交付包含收费商品和Canva管理资料，不能公开发布或作为买家商品包。\n桌面和手机主要页面已通过本地Edge浏览器检查；真实收款未开通，支付平台沙箱、Canva买家复制仍待验收。\n进入序物设计商城目录，使用Node.js24运行 node --env-file-if-exists=.env server/start.mjs。\n详情见README和docs/验收记录与上线待办.md。\n',encoding='utf-8')
print(json.dumps(records,ensure_ascii=False,indent=2))
