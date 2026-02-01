# Material 3 Styles

Material 3 Stylesは、カラー、タイポグラフィ、形状、エレベーション、アイコン、モーションを通じて視覚言語を定義します。

## Table of Contents

1. [Color](#color)
2. [Typography](#typography)
3. [Elevation](#elevation)
4. [Shape](#shape)
5. [Icons](#icons)
6. [Motion](#motion)

---

## Color

### Color System Overview

Material 3のカラーシステムは、アクセシブルでパーソナライズ可能なカラースキームを作成します。

URL: https://m3.material.io/styles/color/system/overview

### Color Roles

UIエレメントを特定の色に結びつける役割:

#### Primary Colors
- **primary**: アプリの主要色（メインボタン、アクティブ状態）
- **onPrimary**: プライマリ色上のテキスト/アイコン
- **primaryContainer**: プライマリ要素のコンテナ
- **onPrimaryContainer**: コンテナ上のテキスト

#### Secondary & Tertiary
- **secondary**: アクセントカラー
- **tertiary**: 強調やバランス調整

#### Surface Colors
- **surface**: カード、シート、メニューの背景
- **surfaceVariant**: わずかに異なる背景
- **surfaceTint**: エレベーション表現用

#### Semantic Colors
- **error**: エラー状態
- **warning**: 警告（一部実装で利用可能）
- **success**: 成功状態（一部実装で利用可能）

URL: https://m3.material.io/styles/color/roles

### Color Schemes

#### Dynamic Color
ユーザーの壁紙や選択から色を抽出:
- **User-generated**: ユーザーの選択から
- **Content-based**: 画像/コンテンツから抽出

URL: https://m3.material.io/styles/color/dynamic-color/overview

#### Static Color
固定されたカラースキーム:
- **Baseline**: デフォルトのMaterialベースライン
- **Custom brand**: カスタムブランドカラー

URL: https://m3.material.io/styles/color/static/baseline

### Key Colors and Tones

- **Source color**: スキーム生成の起点となる色
- **Tonal palette**: 各キーカラーから生成される13段階のトーン（0, 10, 20, ..., 100）
- Light theme: 通常トーン40をプライマリに使用
- Dark theme: 通常トーン80をプライマリに使用

URL: https://m3.material.io/styles/color/the-color-system/key-colors-tones

### Tools

**Material Theme Builder**: カラースキーム生成、カスタマイズ、エクスポートツール

URL: https://m3.material.io/blog/material-theme-builder-2-color-match

---

## Typography

### Type Scale

Material 3は5つのロール×3つのサイズ = 15のタイプスタイルを定義:

#### Roles
1. **Display**: 大きく短いテキスト（ヒーロー、見出し）
2. **Headline**: 中規模の見出し
3. **Title**: 小さい見出し（アプリバー、リスト項目）
4. **Body**: 本文テキスト
5. **Label**: ボタン、タブ、小さいテキスト

#### Sizes
- **Large**: 最大サイズ
- **Medium**: 標準サイズ
- **Small**: 最小サイズ

#### Example Styles
```
displayLarge: 57sp, -0.25 letter spacing
headlineMedium: 28sp, 0 letter spacing
bodyLarge: 16sp, 0.5 letter spacing
labelSmall: 11sp, 0.5 letter spacing
```

URL: https://m3.material.io/styles/typography/overview

### Fonts

- デフォルト: **Roboto** (Android), **San Francisco** (iOS), **Roboto** (Web)
- カスタムフォントのサポート
- 変数フォントの活用

URL: https://m3.material.io/styles/typography/fonts

### Applying Typography

- セマンティックな使用（見出しにはheadline、本文にはbody）
- 一貫した階層
- 行の高さと余白の適切な設定

URL: https://m3.material.io/styles/typography/applying-type

---

## Elevation

### Overview

エレベーションはZ軸上のサーフェス間の距離を表現します。

URL: https://m3.material.io/styles/elevation/overview

### Elevation Levels

Material 3は6つのエレベーションレベルを定義:

| Level | DP | Use Case |
|-------|-----|----------|
| 0 | 0dp | 通常のサーフェス |
| 1 | 1dp | カード、わずかに浮いた要素 |
| 2 | 3dp | 検索バー |
| 3 | 6dp | FAB（休止状態） |
| 4 | 8dp | ナビゲーションドロワー |
| 5 | 12dp | モーダルボトムシート、ダイアログ |

### Elevation Representation

Material 3では2つの方法でエレベーションを表現:

1. **Shadow**: 影によるエレベーション（Light theme主体）
2. **Surface tint**: サーフェスに色のティントを重ねる（Dark theme主体）

URL: https://m3.material.io/styles/elevation/applying-elevation

---

## Shape

### Overview

形状は、注意の誘導、状態表現、ブランド表現に使用されます。

URL: https://m3.material.io/styles/shape/overview-principles

### Corner Radius Scale

Material 3は5つの形状トークンを定義:

| Token | Default Value | Use Case |
|-------|---------------|----------|
| None | 0dp | フルスクリーン、厳格なレイアウト |
| Extra Small | 4dp | チェックボックス、小さい要素 |
| Small | 8dp | チップ、小さいボタン |
| Medium | 12dp | カード、標準ボタン |
| Large | 16dp | FAB、大きいカード |
| Extra Large | 28dp | ダイアログ、ボトムシート |
| Full | 9999dp | 完全な円形 |

### Shape Morph

**M3 Expressiveの重要機能**: 形状が滑らかに変形するアニメーション

- トランジション時の視覚的な流れ
- ブランド表現の強化
- ユーザーの注意を引く

URL: https://m3.material.io/styles/shape/shape-morph

---

## Icons

### Material Symbols

Material Symbolsは可変アイコンフォント:

#### Styles
- **Outlined**: 線のみのスタイル（デフォルト）
- **Filled**: 塗りつぶしスタイル
- **Rounded**: 丸みを帯びたスタイル
- **Sharp**: シャープなスタイル

#### Variable Features
- **Weight**: 線の太さ（100-700）
- **Grade**: 視覚的な重み（-25 to 200）
- **Optical size**: 表示サイズ最適化（20, 24, 40, 48dp）
- **Fill**: 塗りつぶし状態（0-1）

#### Sizes
- 20dp: 密なレイアウト
- 24dp: 標準サイズ
- 40dp: タッチターゲット拡大
- 48dp: 大きいタッチターゲット

URL: https://m3.material.io/styles/icons/overview

### Custom Icons

カスタムアイコンのデザインガイドライン:
- 24×24dpグリッド
- 2dpストローク幅
- 2dpの角丸
- 一貫したメタファー

URL: https://m3.material.io/styles/icons/designing-icons

---

## Motion

**M3 Expressiveの中核要素**: モーションは、UIを表現豊かで使いやすくします。

URL: https://m3.material.io/styles/motion/overview

### Motion Principles

1. **Informative**: ユーザーに情報を伝える
2. **Focused**: 注意を適切に誘導
3. **Expressive**: 感情的なエンゲージメントを高める

URL: https://m3.material.io/styles/motion/overview/how-it-works

### Easing and Duration

#### Easing Types

Material 3は4つのイージングカーブを定義:

1. **Emphasized**: 劇的で表現豊かな動き
   - Decelerate: cubic-bezier(0.05, 0.7, 0.1, 1.0)
   - Accelerate: cubic-bezier(0.3, 0.0, 0.8, 0.15)
   - Standard: cubic-bezier(0.2, 0.0, 0, 1.0)

2. **Standard**: バランスの取れた標準的な動き
   - cubic-bezier(0.2, 0.0, 0, 1.0)

3. **Emphasized Decelerate**: 要素が画面に入る
   - cubic-bezier(0.05, 0.7, 0.1, 1.0)

4. **Emphasized Accelerate**: 要素が画面から出る
   - cubic-bezier(0.3, 0.0, 0.8, 0.15)

#### Duration Guidelines

| Element Change | Duration |
|----------------|----------|
| Small (icon state) | 50-100ms |
| Medium (component state) | 250-300ms |
| Large (layout change) | 400-500ms |
| Complex transition | 500-700ms |

**重要**: 長すぎるアニメーション（>1000ms）は避ける

URL: https://m3.material.io/styles/motion/easing-and-duration

### Transitions

ナビゲーション時のトランジションパターン:

#### Transition Types

1. **Container transform**: コンテナが変形して次の画面へ
2. **Shared axis**: 共通軸に沿った移動（X, Y, Z軸）
3. **Fade through**: フェードアウト→フェードイン
4. **Fade**: シンプルなフェード

#### When to Use Each

- **Container transform**: リスト項目→詳細画面
- **Shared axis X**: タブ切り替え、水平ナビゲーション
- **Shared axis Y**: ステッパー、垂直ナビゲーション
- **Shared axis Z**: 前後のナビゲーション（戻る/進む）
- **Fade through**: コンテンツ更新（関連性が低い）
- **Fade**: オーバーレイ、補助的な変更

URL: https://m3.material.io/styles/motion/transitions/transition-patterns

### M3 Expressive Motion

**新しい表現豊かなモーションシステム**:

- より大胆なアニメーション
- カスタマイズ可能なモーションテーマ
- ブランド表現の強化

URL: https://m3.material.io/blog/m3-expressive-motion-theming

---

## References

- Material Design 3 Styles: https://m3.material.io/styles/
- Material Theme Builder: https://material-foundation.github.io/material-theme-builder/
- Material Symbols: https://fonts.google.com/icons
