# Material 3 Foundations

Material 3 Foundationsは、すべてのMaterialインターフェースの基盤となる設計原則とパターンを定義します。

## Table of Contents

1. [Accessibility](#accessibility)
2. [Layout](#layout)
3. [Interaction](#interaction)
4. [Content Design](#content-design)
5. [Design Tokens](#design-tokens)
6. [Adaptive Design](#adaptive-design)

---

## Accessibility

### Core Principles
- 多様な能力を持つユーザーのための設計
- スクリーンリーダーなどの支援技術との統合
- WCAG準拠のコントラスト比

### Key Areas

#### Structure and Elements
- 直感的なレイアウト階層
- アクセシブルなUI要素の設計
- フォーカス管理とナビゲーション

URL: https://m3.material.io/foundations/designing/structure

#### Color Contrast
- WCAG準拠のカラーコントラスト
- テキストとUIコントロールの視認性
- 4.5:1（通常テキスト）、3:1（大きいテキスト、UIコンポーネント）

URL: https://m3.material.io/foundations/designing/color-contrast

#### Text Accessibility
- テキストリサイズのサポート（200%まで）
- アクセシブルなテキスト切り詰め
- 明確で適応可能な文章

URL: https://m3.material.io/foundations/writing/text-resizing

---

## Layout

### Understanding Layout

#### Core Components
- **Regions**: 画面の主要エリア（ヘッダー、本文、ナビゲーション）
- **Columns**: グリッドシステムの基本単位
- **Gutters**: カラム間のスペース
- **Spacing**: 4dpベースの一貫したスペーシングシステム

URL: https://m3.material.io/foundations/layout/understanding-layout/overview

### Window Size Classes

画面サイズに応じたレスポンシブデザイン:

| Size Class | Width | Typical Device | Key Patterns |
|-----------|-------|---------------|--------------|
| Compact | <600dp | Phone | Single pane, bottom nav |
| Medium | 600-840dp | Tablet (portrait) | Dual pane optional, nav rail |
| Expanded | >840dp | Tablet (landscape), Desktop | Dual/multi pane, nav drawer |
| Large/XL | >1240dp | Large screens, TV | Multi-pane, extensive nav |

URL: https://m3.material.io/foundations/layout/applying-layout/window-size-classes

### Canonical Layouts

よく使われるレイアウトパターン:

1. **List-detail**: マスター・詳細ナビゲーション
2. **Feed**: コンテンツフィード
3. **Supporting pane**: 補助コンテンツパネル

URL: https://m3.material.io/foundations/layout/canonical-layouts/overview

---

## Interaction

### States

#### Visual States
- **Enabled**: デフォルト状態
- **Hover**: ポインタがホバーしている状態（デスクトップ）
- **Focused**: キーボードフォーカス
- **Pressed**: アクティブに押されている状態
- **Dragged**: ドラッグ中
- **Disabled**: 無効化状態

#### State Layers
半透明なオーバーレイで状態を視覚的に示す:
- Hover: 8% opacity
- Focus: 12% opacity
- Press: 12% opacity

URL: https://m3.material.io/foundations/interaction/states/state-layers

### Gestures

モバイルインターフェース向けタッチジェスチャー:
- Tap: 基本的な選択
- Long press: コンテキストメニュー
- Drag: 移動、並べ替え
- Swipe: ナビゲーション、削除
- Pinch: ズーム

URL: https://m3.material.io/foundations/interaction/gestures

### Selection

選択インタラクションパターン:
- **Single selection**: ラジオボタン、リスト項目
- **Multi selection**: チェックボックス、選択可能なリスト

URL: https://m3.material.io/foundations/interaction/selection

---

## Content Design

### UX Writing Principles

1. **Clear**: 明確で理解しやすい
2. **Concise**: 簡潔で要点を押さえた
3. **Useful**: ユーザーのニーズに応える
4. **Consistent**: 用語とトーンの一貫性

### Notifications

効果的な通知コンテンツ:
- アクション可能な情報
- 明確な次のステップ
- ユーザーコンテキストの理解

URL: https://m3.material.io/foundations/content-design/notifications

### Alt Text

アクセシブルな画像説明:
- 装飾的画像: 空のalt属性
- 機能的画像: アクションを説明
- 情報的画像: 内容を簡潔に説明

URL: https://m3.material.io/foundations/content-design/alt-text

### Global Writing

国際的なオーディエンス向けの文章:
- ローカライゼーションを考慮した単語選択
- 文化的に中立な表現
- 翻訳しやすい文法構造

URL: https://m3.material.io/foundations/content-design/global-writing/overview

---

## Design Tokens

### What are Design Tokens?

デザイントークンは、デザイン、ツール、コード全体で使用される設計上の決定の最小単位:

- **Color tokens**: primary, secondary, surface, error など
- **Typography tokens**: displayLarge, bodyMedium など
- **Shape tokens**: cornerRadius, roundedCorner など
- **Motion tokens**: duration, easing curves

### Benefits

- デザインとコード間の一貫性
- テーマのカスタマイズが容易
- プラットフォーム間での統一

URL: https://m3.material.io/foundations/design-tokens/overview

---

## Adaptive Design

### Principles

- **Responsive**: ウィンドウサイズに応じた調整
- **Adaptive**: デバイス特性に応じた最適化
- **Contextual**: 使用コンテキストを考慮

### Key Strategies

1. Window size classesに基づくレイアウト調整
2. 入力方式（タッチ、マウス、キーボード）への対応
3. デバイス機能（カメラ、位置情報等）の活用
4. オフラインとオンラインシナリオの対応

URL: https://m3.material.io/foundations/adaptive-design

---

## References

- Material Design 3 Foundations: https://m3.material.io/foundations/
- Glossary: https://m3.material.io/foundations/glossary
