// Business-input equivalences, not a general Chinese translation library.
// Every replacement preserves UTF-16 length so command captures can keep product spelling.
const characters = {
  訂:'订', 單:'单', 狀:'状', 態:'态', 詢:'询', 編:'编', 號:'号', 進:'进', 現:'现',
  麼:'么', 麽:'么', 時:'时', 後:'后', 確:'确', 認:'认', 還:'还', 營:'营',
  業:'业', 幾:'几', 點:'点', 開:'开', 門:'门', 買:'买', 賣:'卖', 購:'购', 貨:'货',
  庫:'库', 預:'预', 幫:'帮', 發:'发', 遞:'递', 過:'过', 價:'价', 錢:'钱', 額:'额',
  對:'对', 賬:'账', 運:'运', 輸:'输', 與:'与', 錄:'录', 冊:'册', 註:'注', 隱:'隐',
  圖:'图', 細:'细', 線:'线', 鍵:'键', 盤:'盘', 無:'无', 隨:'随', 機:'机', 倉:'仓',
  裡:'里', 裏:'里', 餘:'余', 險:'险', 務:'务', 權:'权', 間:'间', 產:'产', 産:'产',
  尋:'寻', 請:'请', 問:'问', 這:'这', 個:'个', 嗎:'吗', 沒:'没', 夠:'够', 們:'们',
  妳:'你', 計:'计', 遲:'迟', 選:'选', 擇:'择', 數:'数', 釋:'释', 應:'应',
  電:'电', 腦:'脑', 蘋:'苹', 藍:'蓝', 螢:'荧', 檯:'台', 臺:'台', 記:'记', 憶:'忆',
  體:'体', 載:'载', 寫:'写', 讀:'读', 滿:'满', 溫:'温', 壺:'壶', 燈:'灯',
};

export function normalizeBusinessText(text) {
  return [...text].map(character => characters[character] ?? character).join('').replaceAll('甚么', '什么');
}

export function normalizeProductQuery(text) {
  return normalizeBusinessText(text).replaceAll('滑鼠', '鼠标');
}
