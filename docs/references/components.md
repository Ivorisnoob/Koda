# Material 3 Components

Material 3は38のドキュメント化されたコンポーネントを提供します。各コンポーネントには、概要、ガイドライン、仕様、アクセシビリティのサブページがあります。

## Table of Contents

1. [Action Components](#action-components)
2. [Selection and Input Components](#selection-and-input-components)
3. [Navigation Components](#navigation-components)
4. [Containment and Layout Components](#containment-and-layout-components)
5. [Communication Components](#communication-components)

---

## Action Components

ユーザーがアクションを実行するためのコンポーネント。

### Buttons

#### Common Buttons
主要なアクションのための標準的なボタン。

**Variants:**
- **Filled**: 最も高い強調度、プライマリアクション
- **Filled Tonal**: 中程度の強調度、セカンダリアクション
- **Outlined**: 線のみ、中程度の強調度
- **Elevated**: 影付き、強調が必要だがFilledほどではない
- **Text**: 最も低い強調度、補助的なアクション

**Usage Guidelines:**
- 1つの画面にFilledボタンは1つまで推奨
- ボタンの階層を明確に（Filled > Tonal > Outlined > Text）
- 最小タッチターゲット: 48×48dp
- ラベルは動詞で開始（例: "保存", "送信", "削除"）

URL: https://m3.material.io/components/buttons/overview

#### Icon Buttons
コンパクトな補助的アクションボタン。

**Variants:**
- Standard
- Filled
- Filled Tonal
- Outlined

**Usage:**
- 繰り返し使用されるアクション（お気に入り、共有、削除）
- 限られたスペース
- アイコンのみで意味が明確な場合

URL: https://m3.material.io/components/icon-buttons/overview

#### Floating Action Button (FAB)
画面の主要アクションのための浮遊ボタン。

**Types:**
- **FAB**: 標準的なFAB
- **Small FAB**: 小さいFAB
- **Large FAB**: 大きいFAB
- **Extended FAB**: テキストラベル付きFAB

**Guidelines:**
- 1画面に1つのFAB推奨
- 最も重要なアクションのみ
- 配置: 通常は右下
- スクロール時の動作を考慮（隠す/縮小）

URL: https://m3.material.io/components/floating-action-button/overview

#### Segmented Buttons
関連するオプションの単一選択または複数選択グループ。

**Usage:**
- ビューの切り替え（リスト/グリッド）
- フィルタリング（カテゴリ選択）
- 設定オプション

**Guidelines:**
- 2-5個のオプション推奨
- 各オプションは簡潔に（1-2語）
- アイコン+テキストまたはテキストのみ

URL: https://m3.material.io/components/segmented-buttons/overview

---

## Selection and Input Components

ユーザーが選択や入力を行うためのコンポーネント。

### Checkbox
リストから複数のアイテムを選択。

**States:**
- Unchecked
- Checked
- Indeterminate（部分選択）

**Usage:**
- 複数選択
- オン/オフ設定（ただしSwitchの方が適切な場合も）
- リスト項目の選択

URL: https://m3.material.io/components/checkbox/guidelines

### Radio Button
セットから1つのオプションを選択。

**Usage:**
- 相互排他的なオプション（1つのみ選択可能）
- すべてのオプションを表示する必要がある場合
- 2-7個のオプション推奨

**Guidelines:**
- デフォルト選択肢を提供
- オプションは垂直に配置推奨
- ラベルはクリック可能に

URL: https://m3.material.io/components/radio-button/overview

### Switch
バイナリのオン/オフ切り替え。

**Usage:**
- 即座に効果が反映される設定
- 単一アイテムの有効/無効化
- リスト内の個別項目の切り替え

**vs Checkbox:**
- Switch: 即座に効果、状態の切り替え
- Checkbox: 保存が必要、複数選択

URL: https://m3.material.io/components/switch/guidelines

### Text Fields
テキスト入力用のフォームフィールド。

**Types:**
- **Filled**: デフォルト、背景塗りつぶし
- **Outlined**: 線のみ、フォーム内で推奨

**Elements:**
- Label: 入力内容の説明
- Input text: ユーザー入力
- Helper text: 補助的な説明
- Error text: エラーメッセージ
- Leading/Trailing icons: アイコン

**Guidelines:**
- ラベルは簡潔に
- プレースホルダーは補助的な例として使用
- エラーは具体的に（"無効な入力" ではなく "有効なメールアドレスを入力してください"）

URL: https://m3.material.io/components/text-fields/overview

### Chips
コンパクトな情報要素。

**Types:**
- **Assist**: アクションやヘルプのサジェスト
- **Filter**: コンテンツのフィルタリング
- **Input**: ユーザー入力（タグ、連絡先）
- **Suggestion**: 動的な提案

**Usage:**
- タグや属性の表示
- フィルタリングオプション
- 選択されたアイテムの表示

URL: https://m3.material.io/components/chips/guidelines

### Sliders
範囲内の値を選択。

**Types:**
- Continuous: 連続的な値
- Discrete: 離散的な値（ステップ付き）

**Usage:**
- 音量、明るさ調整
- 価格範囲選択
- 数値設定

URL: https://m3.material.io/components/sliders/specs

### Date Pickers / Time Pickers
日付と時刻の選択。

**Date Picker Modes:**
- Modal: ダイアログ形式
- Docked: インライン表示

**Time Picker Types:**
- Dial: ダイヤル形式
- Input: テキスト入力形式

URL: https://m3.material.io/components/date-pickers

---

## Navigation Components

アプリ内のナビゲーションを提供するコンポーネント。

### Navigation Bar
モバイル向けボトムナビゲーション。

**Guidelines:**
- 3-5個の主要な目的地
- アイコン+ラベル（アイコンのみは避ける）
- 常に表示（スクロールしても固定）
- Compact window size class向け

URL: https://m3.material.io/components/navigation-bar/overview

### Navigation Drawer
サイドナビゲーション。

**Types:**
- **Standard**: 画面端から開閉
- **Modal**: オーバーレイ形式

**Usage:**
- 5個以上の目的地
- Medium/Expanded window size class
- アプリの主要セクション

URL: https://m3.material.io/components/navigation-drawer/overview

### Navigation Rail
垂直方向のナビゲーション（中型画面）。

**Usage:**
- Medium window size class（タブレット縦向き）
- 3-7個の目的地
- 画面左端に固定

URL: https://m3.material.io/components/navigation-rail/overview

### Top App Bar
画面上部のタイトルとアクション。

**Types:**
- **Small**: 標準的なアプリバー
- **Medium**: 中サイズ（スクロールで縮小）
- **Large**: 大サイズ（スクロールで縮小）

**Elements:**
- Navigation icon: 戻る、メニュー
- Title: 画面タイトル
- Action icons: 主要なアクション（最大3つ推奨）

URL: https://m3.material.io/components/app-bars/overview

### Tabs
コンテンツを複数のビューに整理。

**Types:**
- Primary tabs: メインコンテンツの切り替え
- Secondary tabs: サブセクションの切り替え

**Guidelines:**
- 2-6個のタブ推奨
- ラベルは簡潔に（1-2語）
- スワイプジェスチャーでの切り替えをサポート

URL: https://m3.material.io/components/tabs/guidelines

---

## Containment and Layout Components

コンテンツを整理・表示するためのコンポーネント。

### Cards
関連情報をまとめたコンテナ。

**Types:**
- **Elevated**: 影付き
- **Filled**: 背景塗りつぶし
- **Outlined**: 線のみ

**Usage:**
- 異なるコンテンツのコレクション
- アクション可能なコンテンツ
- エントリーポイント

**Guidelines:**
- 過度に使用しない（リストで十分な場合も）
- 明確なアクションを提供
- 情報の階層を維持

URL: https://m3.material.io/components/cards/guidelines

### Lists
垂直方向のテキストと画像のインデックス。

**Types:**
- Single-line
- Two-line
- Three-line

**Elements:**
- Leading element: アイコン、画像、チェックボックス
- Primary text: メインテキスト
- Secondary text: サブテキスト
- Trailing element: メタ情報、アクション

**Usage:**
- 同質なコンテンツのコレクション
- スキャン可能な情報
- 詳細へのエントリーポイント

URL: https://m3.material.io/components/lists/overview

### Carousel
スクロール可能なビジュアルアイテムのコレクション。

**Types:**
- Hero: 大きい、フォーカスされたアイテム
- Multi-browse: 複数アイテム表示
- Uncontained: フルブリード

**Usage:**
- 画像ギャラリー
- プロダクトショーケース
- オンボーディング

URL: https://m3.material.io/components/carousel/overview

### Bottom Sheets / Side Sheets
追加コンテンツを表示するサーフェス。

**Types:**
- **Standard**: 永続的、画面の一部
- **Modal**: 一時的、フォーカスが必要

**Bottom Sheet Usage:**
- コンテキストアクション
- 追加オプション
- Mobile向け

**Side Sheet Usage:**
- 詳細情報、フィルタ
- Tablet/Desktop向け

URL: https://m3.material.io/components/bottom-sheets/overview

---

## Communication Components

ユーザーにフィードバックや情報を伝えるコンポーネント。

### Dialogs
ユーザーアクションが必要な重要なプロンプト。

**Types:**
- **Basic**: タイトル、本文、アクション
- **Full-screen**: フルスクリーンダイアログ（モバイル）

**Usage:**
- 重要な決定（削除確認など）
- 必須の情報入力
- エラーや警告

**Guidelines:**
- タイトルは質問形式推奨
- アクションは明確に（"削除"、"キャンセル"）
- 破壊的なアクションは右側に配置しない

URL: https://m3.material.io/components/dialogs/guidelines

### Snackbar
プロセスの簡潔な更新を画面下部に表示。

**Usage:**
- 操作完了の確認（"メッセージを送信しました"）
- 軽微なエラー通知
- オプショナルなアクション提供

**Guidelines:**
- 表示時間: 4-10秒
- 1行のメッセージ推奨
- 最大1つのアクション
- 重要な情報には使用しない（Dialogを使用）

URL: https://m3.material.io/components/snackbar/overview

### Badges
ナビゲーション項目上の通知とカウント。

**Types:**
- Numeric: 数値表示（1-999）
- Dot: ドット表示（新着あり）

**Usage:**
- 未読通知の数
- 新着コンテンツのインジケーター

URL: https://m3.material.io/components/badges/overview

### Progress Indicators
進行中のプロセスのステータス表示。

**Types:**
- **Circular**: 円形、不定期または確定的
- **Linear**: 線形、確定的な進捗

**Usage:**
- Circular: ローディング、処理中
- Linear: ファイルアップロード、ダウンロード

**Guidelines:**
- 2秒以上かかる処理で表示
- 可能な限り確定的な進捗を使用
- 進捗率がわからない場合は不定期

URL: https://m3.material.io/components/progress-indicators/overview

### Tooltips
コンテキストラベルとメッセージ。

**Types:**
- Plain: テキストのみ
- Rich: テキスト+アイコン/画像

**Usage:**
- アイコンボタンの説明
- 切り詰められたテキストの完全版
- 補助的な情報

**Guidelines:**
- 簡潔に（1行推奨）
- 重要な情報には使用しない
- タッチデバイスではlong press

URL: https://m3.material.io/components/tooltips/guidelines

### Menus
一時的なサーフェース上の選択肢リスト。

**Types:**
- Standard menu
- Dropdown menu
- Exposed dropdown menu（選択状態を表示）

**Usage:**
- コンテキストメニュー
- 選択オプション
- アクションのリスト

**Guidelines:**
- 2-7個のアイテム推奨
- アイコンはオプション
- 破壊的なアクションは分離

URL: https://m3.material.io/components/menus/overview

### Search
検索バーとサジェスト。

**Elements:**
- Search bar: 検索入力フィールド
- Search view: 全画面検索インターフェース

**Usage:**
- アプリ内検索
- フィルタリング
- サジェスト表示

URL: https://m3.material.io/components/search/overview

---

## Component Selection Guide

### Action Selection

| Need | Component |
|------|-----------|
| Primary screen action | FAB or Filled Button |
| Secondary action | Tonal/Outlined Button |
| Tertiary action | Text Button |
| Compact action | Icon Button |
| Toggle between 2-5 options | Segmented Button |

### Input Selection

| Need | Component |
|------|-----------|
| Single choice from list | Radio Button |
| Multiple choices | Checkbox |
| On/Off toggle | Switch |
| Text input | Text Field |
| Date selection | Date Picker |
| Value from range | Slider |
| Tags or attributes | Input Chips |

### Navigation Selection

| Window Size | Primary Nav | Secondary Nav |
|-------------|-------------|---------------|
| Compact (<600dp) | Navigation Bar | Tabs |
| Medium (600-840dp) | Navigation Rail | Tabs |
| Expanded (>840dp) | Navigation Drawer | Tabs, Top App Bar |

---

## References

- Material Design 3 Components: https://m3.material.io/components/
- All Components List: https://m3.material.io/components/all-buttons
