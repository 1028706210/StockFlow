import json, csv, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
descriptions={
'a-social':dict(title='栖日 · 品牌社交模板',subtitle='6 种表达，让品牌有自己的语气。',description='从品牌亮相到新品介绍，把日常内容整理成一套有辨识度的视觉。6 个不同用途的原创版式，包含品牌介绍、新品发布、三点卖点、活动邀请、使用步骤和品牌宣言。',price=5900),
'b-promo':dict(title='栖日 · 促销海报模板',subtitle='先看懂信息，再记住你的品牌。',description='4 页信息层级清晰的竖版海报，覆盖新品促销、组合优惠、活动报名与节日礼赠。标题、价格、优惠说明、商品展示区域和行动按钮各有位置，可按实际活动替换。',price=4900),
'c-icons':dict(title='日常商店 · 线性图标',subtitle='12 个小图标，补齐品牌网站的细节。',description='门店、购物袋、包裹、配送、标签、收据、付款、客服、日历、礼盒、退换与安全核验。统一 24 px 网格、1.75 px 描边与圆端点，适用于网站栏目、商品说明和营销页面。',price=2900),
'd-decor':dict(title='有形 · 品牌装饰素材',subtitle='12 种构图，为重点留一个好位置。',description='放射徽章、拱形边框、折叠丝带、叶冠、票券、波浪标签、角花、蝴蝶结、星芒组合、植物枝条、编织纹章与轨道标签。几何图形和自然线条相配，为品牌海报与社交内容增加层次。',price=2900)
}
products=[]
for id,d in descriptions.items():
    path=ROOT/'private/products'/id
    mf=json.loads((path/'manifest.json').read_text(encoding='utf-8'))
    assert (path/'download.zip').is_file()
    with zipfile.ZipFile(path/'download.zip') as z: assert z.testzip() is None
    previews={key:f'/previews/{id}/{key}.png' for key in ['cover','overview','detail','usage']}
    for preview in previews.values(): assert (ROOT/'public'/preview.lstrip('/')).is_file()
    template=id in ['a-social','b-promo']
    entry=dict(id=id,title=d['title'],subtitle=d['subtitle'],description=d['description'],category='canva' if template else 'svg',
      suggestedPriceMinor=d['price'],currency='CNY',priceConfirmed=False,fulfillmentReady=not template,saleStatus='blocked' if template else 'ready',
      editable='可编辑文字、配色与几何布局；Canva 图形逐页编辑仍待人工验收' if template else '可编辑路径、颜色、描边和尺寸',
      requirements=['Canva 账号；买家复制链接和免费账号完整导出待核验','本地 PPTX 可用兼容软件打开，字体替换可能影响字形'] if template else ['浏览器可预览 SVG','编辑可使用 Inkscape、Figma、Illustrator 或文本编辑器'],
      formats=['Canva（已导入，交付待核验）','PPTX','PNG','SVG','JSON'] if template else ['SVG','PNG'],
      dimensions=f"1080 × {1080 if id=='a-social' else 1350} px" if template else ('SVG 24 × 24 viewBox；PNG 24 / 512 px' if id=='c-icons' else 'SVG 160 × 160 viewBox；PNG 256 / 1024 px'),
      included=([f"{mf['pageCount']} 页原创版式、1 个可编辑 PPTX",f"{mf['pageCount']} 张完整 PNG 及对应版式 SVG",'布局 JSON、生成器、使用说明与授权草案','Canva 买家复制链接待补充，当前不可结算'] if template else ['12 个独立 SVG 源文件','24 张透明 PNG（每个图形两种尺寸）','1 张全套总览图','使用说明、文件清单、SHA-256 校验与授权草案','1 个 ZIP 打包文件（40 个交付文件）']),
      previews=previews,templateLinkVerified=False if template else None)
    entry['pageCount' if template else 'assetCount']=mf['pageCount' if template else 'assetCount']
    products.append(entry)
data=dict(store=dict(name='序物设计',workingName=True,pricesConfirmed=False,description='原创数字素材工作室',contactEmail=None,region=None,currency='CNY',policiesConfirmed=False),products=products)
(ROOT/'public/catalog.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
(ROOT/'docs').mkdir(exist_ok=True)
with (ROOT/'docs/商品数据表.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.writer(f);writer.writerow(['商品ID','名称','分类','页面或素材数','尺寸','格式','建议价','建议币种（待确认）','正式价格确认','交付就绪','正式可售'])
    for p in products:writer.writerow([p['id'],p['title'],p['category'],p.get('pageCount',p.get('assetCount')),p['dimensions'],' / '.join(p['formats']),p['suggestedPriceMinor']/100,p['currency'],'否','待复制链接核验' if p['category']=='canva' else '源文件完成','否'])
print('目录由4个真实商品清单与ZIP生成，建议价待确认，正式收款保持关闭。')
