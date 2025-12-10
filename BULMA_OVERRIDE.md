# Bulma Override Summary

## Problem
Bulma CSS framework was applying default colors that interfered with our simple dark-on-light color scheme.

## Solution
Completely overrode all Bulma color variables and classes to enforce our design system.

## Changes Made

### 1. Overrode Bulma CSS Variables
```css
--bulma-text: #1a1a1a              /* Very dark text */
--bulma-text-strong: #1a1a1a       /* Very dark bold text */
--bulma-text-light: #333333        /* Dark gray */
--bulma-scheme-main: #fefefe       /* Paper white */
--bulma-background: #fafafa        /* Page background */
--bulma-body-color: #1a1a1a        /* Body text */
--bulma-strong-color: #1a1a1a      /* Strong text */
--bulma-border: #e0e0e0            /* Light borders */
```

### 2. Overrode Bulma Text Helper Classes
```css
.has-text-grey          → #333333 (dark gray)
.has-text-grey-light    → #757575 (medium gray)
.has-text-white         → #1a1a1a (forced to dark!)
.has-text-black         → #1a1a1a (dark)
.has-text-link          → #2563eb (readable blue)
```

### 3. Global Element Overrides
```css
body                    → #fafafa bg, #1a1a1a text
strong, b              → #1a1a1a (very dark)
.title                 → #1a1a1a (very dark)
.subtitle              → #333333 (dark gray)
.box, .card            → #fefefe bg, #1a1a1a text
.table                 → #fefefe bg, #1a1a1a text
input, textarea        → #fefefe bg, #1a1a1a text
label                  → #1a1a1a (very dark)
```

### 4. Forced Dark Text Globally
```css
html                   → #1a1a1a default color
div, section, main     → inherit (dark)
.container             → #1a1a1a
.content *             → #1a1a1a
ul, ol, li             → #1a1a1a
```

### 5. Exception: Navbar
The navbar keeps white text on dark background:
```css
.navbar.is-primary     → #1a4a8a bg, #ffffff text
.navbar-dropdown       → #fefefe bg, #1a1a1a text
```

## Result

✅ **All Bulma interference removed**
✅ **All text is dark (#1a1a1a - #757575 range)**
✅ **All backgrounds are light (#fafafa - #fefefe range)**
✅ **Consistent across entire site**
✅ **Maximum readability**

## Files Modified
- `/src/main/resources/static/css/main.css` - Added 150+ lines of Bulma overrides

## Testing
All Bulma classes now respect our dark-on-light color scheme:
- Text helper classes: ✅ All dark
- Background classes: ✅ All light
- Component colors: ✅ Forced to dark text
- Form elements: ✅ Dark text on light backgrounds
- Tables: ✅ Dark text, no striping
- Tags/badges: ✅ Dark text on light backgrounds

No more Bulma interference!
