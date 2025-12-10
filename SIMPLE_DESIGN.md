# Simple Design System - Dark Text on Paper White

## Overview
The EME application now uses a simple, highly readable design:
- **Background**: Paper white (#fafafa)
- **Cards**: Slightly lighter (#fefefe)
- **Text**: Very dark (#1a1a1a, #333333, #666666)

## Color Palette

### Backgrounds
| Color | Hex | Usage |
|-------|-----|-------|
| Paper White | #fafafa | Page background |
| Card White | #fefefe | Box/card backgrounds |
| Hover Gray | #f5f5f5 | Hover states |

### Text Colors
| Color | Hex | Usage | Example |
|-------|-----|-------|---------|
| Primary Text | #1a1a1a | Body text, headings | Main content |
| Secondary Text | #333333 | Subtitles, labels | Form labels, table headers |
| Muted Text | #666666 | Helper text, timestamps | "Created at..." |
| Disabled Text | #999999 | Disabled states | Inactive buttons |

### Semantic Colors
All use light backgrounds with dark text:

**Success** (Green)
- Background: #d4edda (light green)
- Text: #155724 (dark green)

**Info** (Cyan)
- Background: #d1ecf1 (light cyan)
- Text: #0c5460 (dark cyan)

**Warning** (Yellow)
- Background: #fff3cd (light yellow)
- Text: #856404 (dark brown)

**Danger** (Red)
- Background: #f8d7da (light red)
- Text: #721c24 (dark red)

## Usage

### HTML Classes
```html
<!-- Text -->
<h1 class="title">Page Title</h1>
<p class="subtitle text-secondary">Subtitle text</p>
<p class="text-muted">Helper text</p>

<!-- Cards -->
<div class="box">Content in paper white box</div>

<!-- Alerts -->
<div class="alert alert-success">Success message</div>
<div class="alert alert-warning">Warning message</div>

<!-- Tables -->
<table class="table">
  <thead>
    <tr><th>Dark text header</th></tr>
  </thead>
  <tbody>
    <tr><td>Dark text content</td></tr>
  </tbody>
</table>
```

### CSS Variables
```css
var(--eme-bg)              /* #fafafa - page background */
var(--eme-card-bg)         /* #fefefe - card background */
var(--eme-text-primary)    /* #1a1a1a - main text */
var(--eme-text-secondary)  /* #333333 - secondary text */
var(--eme-text-muted)      /* #666666 - muted text */
var(--eme-border)          /* #e0e0e0 - borders */
```

## Key Principles

1. **Everything is readable**: All text is dark on light backgrounds
2. **Simple color scheme**: Paper white backgrounds, dark text
3. **No dark mode**: Consistent light theme across all pages
4. **High contrast**: All combinations exceed WCAG AA standards
5. **Clean and minimal**: No unnecessary colors or gradients

## Benefits

✅ **Maximum readability** - Dark text on light backgrounds
✅ **Consistent** - Same colors everywhere
✅ **Accessible** - High contrast ratios
✅ **Simple** - Easy to understand and maintain
✅ **Print-friendly** - Looks good when printed

## Migration from Old System

Old code:
```html
<p style="color: #505050; background: #f8f9fa;">Text</p>
```

New code:
```html
<p class="text-secondary">Text</p>
```

All hard-coded colors have been replaced with design system variables for consistency.
