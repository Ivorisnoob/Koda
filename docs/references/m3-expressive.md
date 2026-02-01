# Material 3 Expressive

M3 Expressiveは、Googleが2024-2025年に導入したMaterial 3の進化版で、より魅力的で感情的に共鳴するインターフェースを実現します。

## Table of Contents

1. [Overview](#overview)
2. [Usability Principles](#usability-principles)
3. [Design Tactics](#design-tactics)
4. [Expressive Motion](#expressive-motion)
5. [Shape and Form](#shape-and-form)
6. [Implementation Guidelines](#implementation-guidelines)

---

## Overview

### What is M3 Expressive?

M3 Expressiveは、標準のMaterial 3を拡張し、以下を実現します:

- **Engaging**: ユーザーの注意を引き、関心を維持
- **Emotionally resonant**: 感情的なつながりを生む
- **User-friendly**: 使いやすさを犠牲にしない
- **Brand expression**: ブランドの個性を表現

### Key Differences from Standard M3

| Aspect | Standard M3 | M3 Expressive |
|--------|-------------|---------------|
| Motion | 控えめ、機能的 | 大胆、表現豊か |
| Shapes | 一貫した角丸 | 動的な形状変形 |
| Emphasis | 明確、シンプル | ドラマチック、インパクト |
| Timing | 速い（200-300ms） | やや長め（400-700ms） |

URL: https://m3.material.io/blog/building-with-m3-expressive

---

## Usability Principles

### Creating Engaging Products

M3 Expressiveは、以下のusability原則に基づきます:

#### 1. Guide Users
ユーザーを適切に誘導する:
- **Motion paths**: アニメーションでフローを示す
- **Visual hierarchy**: 動きで注意を引く
- **Staged reveal**: 段階的に情報を開示

#### 2. Emphasize Actions
重要なアクションを強調:
- **Scale changes**: サイズ変化で重要性を示す
- **Color dynamics**: 色の変化で状態を表現
- **Focused attention**: 1つの要素に注意を集中

#### 3. Provide Feedback
ユーザーのアクションに対する明確なフィードバック:
- **Immediate response**: 即座の視覚的反応
- **State transitions**: 状態変化を明確に表現
- **Completion signals**: アクション完了を示す

URL: https://m3.material.io/foundations/usability/overview

---

## Design Tactics

M3 Expressiveを実装するための具体的なデザイン戦術。

URL: https://m3.material.io/foundations/usability/applying-m-3-expressive

### 1. Emphasized Easing

**Standard easing**よりも劇的な**Emphasized easing**を使用:

```
Emphasized Decelerate: cubic-bezier(0.05, 0.7, 0.1, 1.0)
Emphasized Accelerate: cubic-bezier(0.3, 0.0, 0.8, 0.15)
```

**When to use:**
- 重要なトランジション
- ユーザーの注意を引く必要がある場合
- ブランド表現を強化したい場合

**Example:**
```css
.expressive-enter {
  animation: enter 500ms cubic-bezier(0.05, 0.7, 0.1, 1.0);
}
```

### 2. Extended Duration

標準より長いアニメーション時間:

| Element | Standard | Expressive |
|---------|----------|------------|
| Small changes | 100ms | 150-200ms |
| Medium changes | 250ms | 400-500ms |
| Large transitions | 300ms | 500-700ms |

**Caution:** 1000msを超えないこと

### 3. Exaggerated Scale

スケール変化を誇張:

**Standard:**
- Scale: 1.0 → 1.05（+5%）

**Expressive:**
- Scale: 1.0 → 1.15（+15%）
- Scale: 1.0 → 0.9 → 1.1（bounce effect）

**Example use case:**
- FABのタップアニメーション
- カードの選択状態
- アイコンのアクティブ状態

### 4. Dynamic Color Transitions

色の動的な変化:

**Techniques:**
- Gradient animations: グラデーションの動的変化
- Color pulse: 色のパルス効果
- Hue rotation: 色相の変化

**Example:**
```css
.expressive-button:active {
  background: linear-gradient(45deg, primary, tertiary);
  transition: background 400ms cubic-bezier(0.05, 0.7, 0.1, 1.0);
}
```

### 5. Layered Motion

複数の要素が異なるタイミングで動く:

**Stagger animations:**
- 遅延: 50-100ms per item
- リストアイテムの順次表示
- カードグリッドの表示

**Example timing:**
```
Item 1: 0ms
Item 2: 80ms
Item 3: 160ms
Item 4: 240ms
```

### 6. Shape Morphing

形状の動的な変形（後述）

---

## Expressive Motion

M3 Expressiveの中核となるモーションシステム。

URL: https://m3.material.io/blog/m3-expressive-motion-theming

### Motion Theming System

カスタマイズ可能な新しいモーションテーマシステム:

#### Motion Tokens

**Duration tokens:**
```
motion.duration.short: 150ms
motion.duration.medium: 400ms
motion.duration.long: 600ms
motion.duration.extra-long: 1000ms
```

**Easing tokens:**
```
motion.easing.emphasized: cubic-bezier(0.05, 0.7, 0.1, 1.0)
motion.easing.emphasizedDecelerate: cubic-bezier(0.05, 0.7, 0.1, 1.0)
motion.easing.emphasizedAccelerate: cubic-bezier(0.3, 0.0, 0.8, 0.15)
motion.easing.standard: cubic-bezier(0.2, 0.0, 0, 1.0)
```

### Expressive Transition Patterns

#### 1. Container Transform (Enhanced)

**Standard container transform:**
- Duration: 300ms
- Easing: standard

**Expressive container transform:**
- Duration: 500ms
- Easing: emphasized
- 追加効果: 軽いスケール変化、色の変化

#### 2. Shared Axis (Enhanced)

**Expressive enhancements:**
- より大きいスライド距離（+20%）
- フェード+スケール効果の組み合わせ
- ステージングされた要素の動き

#### 3. Morph Transition

新しいトランジションタイプ:
- 形状の滑らかな変形
- 複数プロパティの同時変化（サイズ、色、形状）
- 有機的な動き

**Example:**
```
Circle → Rounded Rectangle → Full Screen
(300ms) → (200ms)
```

### Micro-interactions

小さいが印象的なインタラクション:

#### Button Press
```
1. Scale down: 0.95 (50ms)
2. Scale up: 1.0 (150ms, emphasized easing)
3. Ripple effect: expanded, slower
```

#### Icon State Change
```
1. Scale out: 0.8 + rotate 15deg (100ms)
2. Icon swap
3. Scale in: 1.0 + rotate 0deg (200ms, emphasized)
```

#### Loading States
```
- Pulse animation: 1.0 → 1.1 → 1.0 (800ms, loop)
- Color shift: primary → tertiary → primary
```

---

## Shape and Form

### Shape Morph

動的な形状変形でブランド表現を強化。

URL: https://m3.material.io/styles/shape/shape-morph

#### Basic Shape Morph

形状の滑らかな変化:

**Example scenarios:**
1. **FAB → Dialog**
   - Circle (56dp) → Rounded rectangle (280×400dp)
   - Duration: 500ms
   - Easing: emphasized decelerate

2. **Chip → Card**
   - Small rounded (32dp) → Medium rounded (card size)
   - Duration: 400ms

3. **Button → Full Width**
   - Fixed width → Full screen width
   - Corner radius維持

#### Advanced Techniques

**Path morphing:**
- SVGパスの変形
- ベジェ曲線の補間
- 複雑な形状間の遷移

**Example SVG morph:**
```svg
<path d="M10,10 L90,10 L90,90 L10,90 Z">
  <animate attributeName="d"
           to="M50,10 L90,50 L50,90 L10,50 Z"
           dur="500ms"
           fill="freeze"/>
</path>
```

### Organic Shapes

より自然で有機的な形状:

**Characteristics:**
- 非対称な角丸
- 流動的なライン
- 自然界からのインスピレーション

**Use cases:**
- ブランド要素
- ヒーローセクション
- イラストレーション

---

## Implementation Guidelines

### When to Use M3 Expressive

#### Good Use Cases ✓
- **Consumer apps**: エンターテイメント、ソーシャル、ゲーム
- **Brand-forward products**: ブランド表現が重要
- **Engagement-critical flows**: オンボーディング、チュートリアル
- **Hero moments**: 重要なマイルストーン、達成

#### Use with Caution ⚠
- **Productivity apps**: 過度なアニメーションは避ける
- **Frequent actions**: 繰り返し使用される操作
- **Data-heavy interfaces**: 情報が優先される場合

#### Avoid ✗
- **Accessibility concerns**: 動きに敏感なユーザー
- **Performance-constrained**: 低スペックデバイス
- **Critical tasks**: エラーや警告の表示

### Balancing Expressiveness and Usability

#### The 80/20 Rule

- **80%**: 標準のM3（速く、機能的）
- **20%**: M3 Expressive（印象的、ブランド表現）

**Example distribution:**
- Standard M3: リスト項目タップ、フォーム入力、設定変更
- M3 Expressive: 画面遷移、主要アクション（FAB）、初回体験

### Respect User Preferences

#### Reduced Motion

`prefers-reduced-motion`メディアクエリを尊重:

```css
@media (prefers-reduced-motion: reduce) {
  * {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

#### Accessibility

- **Vestibular disorders**: 大きい動きを避ける
- **Cognitive load**: 同時に動く要素を制限
- **Focus management**: アニメーション中もフォーカス可能

---

## Practical Examples

### Example 1: Expressive FAB Tap

```css
.fab {
  transition: transform 150ms cubic-bezier(0.05, 0.7, 0.1, 1.0),
              box-shadow 150ms cubic-bezier(0.05, 0.7, 0.1, 1.0);
}

.fab:active {
  transform: scale(0.92);
}

.fab:not(:active) {
  transform: scale(1.0);
}

/* Ripple with longer duration */
.fab::after {
  animation: ripple 600ms cubic-bezier(0.05, 0.7, 0.1, 1.0);
}
```

### Example 2: Card to Detail Transition

```javascript
// Container transform with expressive timing
const expandCard = (card) => {
  card.animate([
    {
      transform: 'scale(1)',
      borderRadius: '12px'
    },
    {
      transform: 'scale(1.02)',
      borderRadius: '28px',
      offset: 0.3
    },
    {
      transform: 'scale(1)',
      borderRadius: '0px'
    }
  ], {
    duration: 500,
    easing: 'cubic-bezier(0.05, 0.7, 0.1, 1.0)',
    fill: 'forwards'
  });
};
```

### Example 3: Staggered List Animation

```css
.list-item {
  opacity: 0;
  transform: translateY(20px);
  animation: fadeInUp 400ms cubic-bezier(0.05, 0.7, 0.1, 1.0) forwards;
}

.list-item:nth-child(1) { animation-delay: 0ms; }
.list-item:nth-child(2) { animation-delay: 80ms; }
.list-item:nth-child(3) { animation-delay: 160ms; }
.list-item:nth-child(4) { animation-delay: 240ms; }

@keyframes fadeInUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
```

---

## Resources and Tools

### Design Tools
- **Material Theme Builder**: M3 Expressiveモーションプリセット
- **Figma Plugins**: Motion timing visualization
- **After Effects**: プロトタイプアニメーション

### Code Libraries
- **Web**: Material Web Components (M3 support)
- **Flutter**: Material 3 with custom motion
- **Android**: Jetpack Compose Material3

### References
- M3 Expressive announcement: https://m3.material.io/blog/building-with-m3-expressive
- Motion theming: https://m3.material.io/blog/m3-expressive-motion-theming
- Usability tactics: https://m3.material.io/foundations/usability/applying-m-3-expressive

---

## Summary Checklist

When implementing M3 Expressive, ensure:

- [ ] Emphasized easing for key transitions
- [ ] Extended durations (but <1000ms)
- [ ] Exaggerated scale changes where appropriate
- [ ] Layered/staggered animations for lists
- [ ] Shape morphing for container transforms
- [ ] Color dynamics for feedback
- [ ] Respect `prefers-reduced-motion`
- [ ] 80/20 balance (Standard M3 vs Expressive)
- [ ] Test on lower-end devices
- [ ] Validate accessibility
