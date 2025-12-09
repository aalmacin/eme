# UI Improvement Plan

## Executive Summary

This document outlines a comprehensive plan to modernize the EME application's user interface. The current implementation uses Spring Boot + Thymeleaf with Bootstrap 5, inline CSS, and selective Vue.js 2 integration. The plan recommends migrating to **Bulma CSS** for a cleaner, more modern aesthetic with no JavaScript dependency.

---

## Current State Analysis

### Technology Stack
| Component | Current | Target |
|-----------|---------|--------|
| Template Engine | Thymeleaf | Thymeleaf (keep) |
| CSS Framework | Bootstrap 5.1.3/5.3.2 | **Bulma 1.0** |
| JavaScript | Vue.js 2 + Vanilla JS + jQuery | HTMX + Alpine.js |
| Styling | Inline `<style>` tags per page | Centralized CSS |
| Design System | Ad-hoc patterns | Documented standards |

### Identified Problems

1. **CSS Duplication**: Same styles repeated across 15+ template files
2. **Mixed CSS Versions**: Bootstrap 5.1.3 (WebJars) and 5.3.2 (CDN) used inconsistently
3. **No Shared Stylesheet**: Each page defines its own inline CSS (~100-200 lines per file)
4. **Outdated Vue.js**: Vue 2 reached end-of-life in December 2023
5. **Inconsistent JavaScript Patterns**: Mix of Vue.js, vanilla JS, and jQuery
6. **No Component Reusability**: UI components duplicated in each template
7. **Accessibility Concerns**: Missing ARIA labels, focus management, keyboard navigation
8. **Mobile Experience**: Bootstrap responsive but not mobile-optimized UX

---

## Improvement Recommendations

### Phase 1: CSS Consolidation (Low Risk, High Impact)

#### 1.1 Create Centralized Stylesheet

**Current**: Inline styles in each template (~2000+ lines of duplicated CSS)

**Proposed**: Create a single `main.css` file

```
src/main/resources/static/css/
├── main.css           # Core application styles
├── components.css     # Reusable component styles
└── utilities.css      # Custom utility classes
```

**Benefits**:
- Single source of truth for styles
- Browser caching improves performance
- Easier maintenance and updates
- Reduced page weight

#### 1.2 Define CSS Custom Properties (Design Tokens)

```css
:root {
  /* Colors */
  --color-primary: #007bff;
  --color-success: #28a745;
  --color-danger: #dc3545;
  --color-warning: #ffc107;
  --color-info: #17a2b8;

  /* Backgrounds */
  --bg-light: #f8f9fa;
  --bg-card: #ffffff;

  /* Spacing */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;

  /* Border Radius */
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;

  /* Shadows */
  --shadow-card: 0 2px 8px rgba(0, 0, 0, 0.1);
  --shadow-modal: 0 4px 20px rgba(0, 0, 0, 0.15);

  /* Typography */
  --font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  --font-size-sm: 0.875rem;
  --font-size-base: 1rem;
  --font-size-lg: 1.25rem;
}
```

#### 1.3 Migrate to Bulma CSS

**Action**: Replace Bootstrap with Bulma 1.0

```gradle
// build.gradle - Remove Bootstrap, add Bulma
// Remove: implementation 'org.webjars:bootstrap:5.1.3'
// Remove: implementation 'org.webjars:jquery:3.6.0'
implementation 'org.webjars.npm:bulma:1.0.2'
```

**Or via CDN** (for quick testing):
```html
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.2/css/bulma.min.css">
```

**Why Bulma over Bootstrap?**
- Pure CSS, no JavaScript dependency
- Cleaner, more intuitive class naming (`is-primary`, `is-large`, `has-text-centered`)
- Modern flexbox-based grid system
- Lighter weight (~25KB vs ~50KB)
- Easier to customize with Sass variables
- More modern, minimal aesthetic out of the box

---

### Phase 2: Design System Implementation (Medium Effort)

#### 2.1 Component Library

Create reusable Thymeleaf fragments for common UI patterns:

```
templates/fragments/
├── layout.html        # Base layout with header/footer
├── navigation.html    # Navigation components
├── forms.html         # Form inputs, selects, checkboxes
├── tables.html        # Data tables with sorting/filtering
├── cards.html         # Card components
├── modals.html        # Modal dialogs
├── alerts.html        # Alert/notification components
├── badges.html        # Status badges
└── buttons.html       # Button variants
```

**Example Fragment** (`fragments/cards.html`):
```html
<!-- Standard Card (Bulma) -->
<th:block th:fragment="card(title, content)">
  <div class="card">
    <header class="card-header" th:if="${title}">
      <p class="card-header-title" th:text="${title}"></p>
    </header>
    <div class="card-content" th:insert="${content}"></div>
  </div>
</th:block>

<!-- Stat Card (Bulma) -->
<th:block th:fragment="stat-card(label, value, variant)">
  <div class="box has-text-centered" th:classappend="${variant}">
    <p class="title is-4" th:text="${value}"></p>
    <p class="subtitle is-6 has-text-grey" th:text="${label}"></p>
  </div>
</th:block>
```

#### 2.2 Consistent Navigation

**Current**: Each page has its own navigation implementation

**Proposed**: Unified navigation component with:
- Consistent placement (top navbar or sidebar)
- Active state highlighting
- Responsive collapse on mobile
- Breadcrumb trail for nested pages

```html
<!-- fragments/navigation.html (Bulma) -->
<nav th:fragment="main-nav" class="navbar is-primary" role="navigation">
  <div class="navbar-brand">
    <a class="navbar-item has-text-weight-bold" href="/">EME</a>
    <a role="button" class="navbar-burger" aria-label="menu" data-target="navMenu">
      <span></span><span></span><span></span>
    </a>
  </div>
  <div id="navMenu" class="navbar-menu">
    <div class="navbar-start">
      <a class="navbar-item" th:classappend="${currentPage == 'words'} ? 'is-active'"
         th:href="@{/words}">Words</a>
      <a class="navbar-item" th:classappend="${currentPage == 'translations'} ? 'is-active'"
         th:href="@{/translations}">Translations</a>
      <a class="navbar-item" th:classappend="${currentPage == 'sentences'} ? 'is-active'"
         th:href="@{/sentences}">Sentences</a>
      <a class="navbar-item" th:classappend="${currentPage == 'sessions'} ? 'is-active'"
         th:href="@{/sessions}">Sessions</a>
    </div>
  </div>
</nav>
```

#### 2.3 Form Standardization

Create consistent form patterns:

```html
<!-- fragments/forms.html (Bulma) -->
<th:block th:fragment="form-group(label, field, help)">
  <div class="field">
    <label class="label" th:text="${label}"></label>
    <div class="control" th:insert="${field}"></div>
    <p class="help" th:if="${help}" th:text="${help}"></p>
  </div>
</th:block>

<!-- Input Field -->
<th:block th:fragment="input(name, placeholder, type)">
  <input class="input" th:type="${type} ?: 'text'"
         th:name="${name}" th:placeholder="${placeholder}">
</th:block>

<!-- Select Field -->
<th:block th:fragment="select(name, options)">
  <div class="select is-fullwidth">
    <select th:name="${name}">
      <option th:each="opt : ${options}" th:value="${opt.value}" th:text="${opt.label}"></option>
    </select>
  </div>
</th:block>

<!-- Button -->
<th:block th:fragment="button(text, type, variant)">
  <button class="button" th:classappend="'is-' + ${variant}" th:type="${type} ?: 'button'">
    <span th:text="${text}"></span>
  </button>
</th:block>
```

---

### Phase 3: JavaScript Modernization (Medium-High Effort)

#### 3.1 Option A: Upgrade to Vue 3 + Composition API

**Pros**:
- Modern reactive framework
- Better TypeScript support
- Smaller bundle size
- Active community and support

**Implementation**:
```html
<!-- Use Vue 3 via CDN for progressive enhancement -->
<script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>
<script>
const { createApp, ref, computed } = Vue;

createApp({
  setup() {
    const word = ref('');
    const features = ref({
      translation: true,
      mnemonics: false,
      audio: false
    });

    return { word, features };
  }
}).mount('#app');
</script>
```

#### 3.2 Option B: HTMX for Hypermedia-Driven UI

**Pros**:
- No build step required
- Works naturally with Thymeleaf
- Reduces JavaScript complexity
- Progressive enhancement friendly

**Implementation**:
```html
<!-- Add HTMX -->
<script src="https://unpkg.com/htmx.org@1.9.10"></script>

<!-- Dynamic table filtering without custom JS -->
<input type="text" name="search"
       hx-get="/words/search"
       hx-trigger="keyup changed delay:300ms"
       hx-target="#words-table-body"
       placeholder="Search words...">

<tbody id="words-table-body">
  <!-- Server returns HTML fragments -->
</tbody>
```

**HTMX Benefits for This App**:
- Replace vanilla JS table filtering with server-side filtering
- Simplify modal create/edit/delete flows
- Enable infinite scroll for large word lists
- Reduce client-side state management complexity

#### 3.3 Option C: Alpine.js for Lightweight Interactivity

**Pros**:
- Minimal learning curve
- No build step
- Perfect for Thymeleaf integration
- Declarative syntax in HTML

**Implementation**:
```html
<script src="https://unpkg.com/alpinejs@3.x.x/dist/cdn.min.js" defer></script>

<div x-data="{ showAdvanced: false, features: { translation: true } }">
  <button @click="showAdvanced = !showAdvanced">
    Advanced Options
  </button>

  <div x-show="showAdvanced" x-transition>
    <!-- Advanced options content -->
  </div>
</div>
```

#### 3.4 Recommendation

**Use HTMX + Alpine.js combination**:
- HTMX for server communication (CRUD, filtering, pagination)
- Alpine.js for client-side interactivity (toggles, modals, form validation)
- Remove Vue.js and jQuery dependencies
- Aligns with Thymeleaf's server-side rendering philosophy

---

### Phase 4: Bulma Migration Guide

#### 4.1 Bootstrap to Bulma Class Mapping

| Bootstrap | Bulma | Notes |
|-----------|-------|-------|
| `container` | `container` | Same concept |
| `row` | `columns` | Flexbox-based |
| `col-md-6` | `column is-6` | Uses `is-*` modifiers |
| `btn btn-primary` | `button is-primary` | Cleaner syntax |
| `btn-lg` | `is-large` | Size modifiers |
| `form-control` | `input` / `textarea` | Direct class on element |
| `form-group` | `field` | Container for form elements |
| `form-label` | `label` | Direct class |
| `alert alert-danger` | `notification is-danger` | Notification component |
| `card` | `card` or `box` | Similar concept |
| `table table-striped` | `table is-striped` | Modifier pattern |
| `badge` | `tag` | Different naming |
| `modal` | `modal` | Requires Alpine.js for toggle |
| `nav navbar` | `navbar` | Simpler structure |
| `text-center` | `has-text-centered` | Utility classes |
| `d-flex` | `is-flex` | Flexbox utilities |
| `mt-3`, `mb-3` | `mt-3`, `mb-3` | Spacing helpers (similar) |

#### 4.2 Bulma Grid System

```html
<!-- Bulma uses columns instead of rows/cols -->
<div class="columns">
  <div class="column is-4">Sidebar</div>
  <div class="column is-8">Main Content</div>
</div>

<!-- Responsive columns -->
<div class="columns is-multiline">
  <div class="column is-12-mobile is-6-tablet is-4-desktop">Card 1</div>
  <div class="column is-12-mobile is-6-tablet is-4-desktop">Card 2</div>
  <div class="column is-12-mobile is-6-tablet is-4-desktop">Card 3</div>
</div>

<!-- Gap control -->
<div class="columns is-variable is-4">
  <!-- 1rem gap between columns -->
</div>
```

#### 4.3 Bulma Components for EME

**Tables** (for words/translations list):
```html
<table class="table is-fullwidth is-striped is-hoverable">
  <thead>
    <tr>
      <th>Word</th>
      <th>Translation</th>
      <th>Actions</th>
    </tr>
  </thead>
  <tbody>
    <tr th:each="word : ${words}">
      <td th:text="${word.original}"></td>
      <td th:text="${word.translation}"></td>
      <td>
        <div class="buttons are-small">
          <a class="button is-info is-outlined">Edit</a>
          <a class="button is-danger is-outlined">Delete</a>
        </div>
      </td>
    </tr>
  </tbody>
</table>
```

**Forms** (for create/edit pages):
```html
<form class="box">
  <div class="field">
    <label class="label">Word</label>
    <div class="control has-icons-left">
      <input class="input" type="text" placeholder="Enter word">
      <span class="icon is-small is-left">
        <i class="fas fa-language"></i>
      </span>
    </div>
  </div>

  <div class="field">
    <label class="label">Language</label>
    <div class="control">
      <div class="select is-fullwidth">
        <select>
          <option>Japanese</option>
          <option>Chinese</option>
        </select>
      </div>
    </div>
  </div>

  <div class="field is-grouped">
    <div class="control">
      <button class="button is-primary">Save</button>
    </div>
    <div class="control">
      <button class="button is-light">Cancel</button>
    </div>
  </div>
</form>
```

**Modals** (with Alpine.js):
```html
<div class="modal" :class="{ 'is-active': showModal }" x-data="{ showModal: false }">
  <div class="modal-background" @click="showModal = false"></div>
  <div class="modal-card">
    <header class="modal-card-head">
      <p class="modal-card-title">Delete Word</p>
      <button class="delete" @click="showModal = false"></button>
    </header>
    <section class="modal-card-body">
      Are you sure you want to delete this word?
    </section>
    <footer class="modal-card-foot">
      <button class="button is-danger">Delete</button>
      <button class="button" @click="showModal = false">Cancel</button>
    </footer>
  </div>
</div>
```

**Tags/Badges** (for language indicators):
```html
<div class="tags">
  <span class="tag is-info">Japanese</span>
  <span class="tag is-success">Completed</span>
  <span class="tag is-warning">Pending</span>
</div>
```

#### 4.4 Bulma Color Customization

Create `src/main/resources/static/css/bulma-custom.css`:

```css
:root {
  /* Override Bulma's default colors */
  --bulma-primary: #3273dc;
  --bulma-link: #485fc7;
  --bulma-info: #3e8ed0;
  --bulma-success: #48c78e;
  --bulma-warning: #ffe08a;
  --bulma-danger: #f14668;

  /* Custom EME colors */
  --eme-bg: #f5f5f5;
  --eme-card-bg: #ffffff;
}

/* Dark mode support */
@media (prefers-color-scheme: dark) {
  :root {
    --bulma-scheme-main: #1a1a2e;
    --bulma-text: #e6e6e6;
    --eme-bg: #0f0f23;
    --eme-card-bg: #1a1a2e;
  }
}
```

---

### Phase 5: Accessibility Improvements

#### 5.1 ARIA Labels and Roles

```html
<!-- Before -->
<button class="button is-primary">Submit</button>

<!-- After -->
<button class="button is-primary" aria-label="Submit word form">
  Submit
</button>

<!-- Table improvements -->
<table class="table is-fullwidth" role="grid" aria-label="Words list">
  <thead>
    <tr>
      <th scope="col" aria-sort="ascending">Word</th>
    </tr>
  </thead>
</table>
```

#### 5.2 Keyboard Navigation

- Ensure all interactive elements are focusable
- Implement proper tab order
- Add keyboard shortcuts for power users
- Focus trapping in modals

#### 5.3 Color Contrast

- Verify WCAG 2.1 AA compliance
- Add focus indicators
- Don't rely solely on color for status

---

### Phase 6: User Experience Enhancements

#### 6.1 Loading States

```html
<!-- Bulma loading button -->
<button class="button is-primary"
        :class="{ 'is-loading': loading }"
        hx-post="/words/generate"
        hx-indicator="this">
  Generate
</button>

<!-- HTMX with Bulma loading class -->
<style>
  .htmx-request.button { @extend .is-loading; }
  .htmx-request .button { @extend .is-loading; }
</style>
```

#### 6.2 Toast Notifications

Replace page-level alerts with Bulma notifications:
```html
<!-- Fixed position notification container -->
<div class="notification-container" x-data="{ notifications: [] }">
  <template x-for="notification in notifications" :key="notification.id">
    <div class="notification" :class="'is-' + notification.type"
         x-show="notification.visible"
         x-transition:leave="transition ease-in duration-300"
         x-transition:leave-start="opacity-100"
         x-transition:leave-end="opacity-0">
      <button class="delete" @click="notification.visible = false"></button>
      <span x-text="notification.message"></span>
    </div>
  </template>
</div>

<style>
.notification-container {
  position: fixed;
  bottom: 1rem;
  right: 1rem;
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
</style>
```

#### 6.3 Improved Tables

- Virtual scrolling for large datasets
- Column resizing
- Saved filter/sort preferences
- Export options (CSV, Excel)

#### 6.4 Dark Mode Support

```css
@media (prefers-color-scheme: dark) {
  :root {
    --bg-light: #1a1a2e;
    --bg-card: #16213e;
    --color-text: #e6e6e6;
  }
}

/* Or manual toggle */
[data-theme="dark"] {
  --bg-light: #1a1a2e;
  /* ... */
}
```

---

## Implementation Roadmap

### Milestone 1: Foundation
- [ ] Add Bulma CSS via WebJars or CDN
- [ ] Create centralized CSS file with design tokens
- [ ] Create base layout fragment with Bulma structure
- [ ] Migrate 1 pilot page (e.g., words/list.html) to Bulma

### Milestone 2: Full Migration
- [ ] Migrate all list pages to Bulma (words, translations, sentences, sessions)
- [ ] Migrate all create/edit forms to Bulma
- [ ] Migrate index.html and review.html to Bulma
- [ ] Remove all Bootstrap references
- [ ] Remove inline CSS from all pages

### Milestone 3: Component Library
- [ ] Create Thymeleaf fragments (navigation, forms, tables, cards, modals)
- [ ] Implement unified navigation across all pages
- [ ] Refactor pages to use shared fragments
- [ ] Add HTMX for server communication
- [ ] Add Alpine.js for client interactivity

### Milestone 4: JavaScript Modernization
- [ ] Migrate index.html from Vue 2 to Alpine.js
- [ ] Migrate review.html from Vue 2 to Alpine.js
- [ ] Replace vanilla JS modals with Alpine.js + Bulma modals
- [ ] Remove jQuery dependency
- [ ] Remove Vue.js dependency

### Milestone 5: Polish
- [ ] Accessibility audit and ARIA fixes
- [ ] Add loading states using Bulma's `is-loading` class
- [ ] Implement toast notifications
- [ ] Add dark mode support
- [ ] Mobile UX optimization

---

## File Structure After Improvements

```
src/main/resources/
├── static/
│   ├── css/
│   │   ├── main.css              # Core styles
│   │   ├── components.css        # Component styles
│   │   └── utilities.css         # Custom utilities
│   └── js/
│       └── app.js                # Shared Alpine.js components
├── templates/
│   ├── fragments/
│   │   ├── layout.html           # Base layout
│   │   ├── navigation.html       # Nav components
│   │   ├── forms.html            # Form components
│   │   ├── tables.html           # Table components
│   │   ├── cards.html            # Card components
│   │   ├── modals.html           # Modal components
│   │   └── toasts.html           # Notification components
│   ├── index.html
│   ├── words/
│   ├── translations/
│   └── ... (other pages)
```

---

## Technology Comparison Summary

| Aspect | Current | Target |
|--------|---------|--------|
| CSS | Inline per page | Centralized + CSS Variables |
| CSS Framework | Bootstrap 5.1/5.3 mixed | **Bulma 1.0** |
| JS Framework | Vue 2 + Vanilla + jQuery | HTMX + Alpine.js |
| Components | Duplicated HTML | Thymeleaf Fragments |
| Accessibility | Minimal | WCAG 2.1 AA Compliant |
| Dark Mode | None | CSS prefers-color-scheme |

---

## Quick Wins (Can Do Today)

1. **Add Bulma via CDN** to one page for testing:
   ```html
   <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.2/css/bulma.min.css">
   ```
2. **Add Font Awesome** for icons (Bulma works well with FA):
   ```html
   <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css">
   ```
3. **Add viewport meta tag** (if missing): `<meta name="viewport" content="width=device-width, initial-scale=1">`
4. **Add favicon**: Improve brand presence
5. **Test Bulma on one page**: Convert `words/list.html` to Bulma as a pilot

---

## Resources

- [Bulma Documentation](https://bulma.io/documentation/)
- [Bulma Extensions](https://bulma.io/extensions/)
- [HTMX Documentation](https://htmx.org/docs/)
- [Alpine.js Documentation](https://alpinejs.dev/)
- [Thymeleaf Layout Dialect](https://ultraq.github.io/thymeleaf-layout-dialect/)
- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Bulma with Spring Boot Tutorial](https://www.baeldung.com/spring-boot-bulma-css)

---

## Conclusion

The recommended approach focuses on:

1. **Bulma CSS** - Modern, lightweight, no-JS framework with cleaner syntax than Bootstrap
2. **CSS Consolidation** - Centralize styles, eliminate duplication
3. **Thymeleaf Fragments** - Reusable components without framework overhead
4. **HTMX + Alpine.js** - Modern interactivity that aligns with server-side rendering
5. **Accessibility** - Ensure the app is usable by everyone

### Why Bulma?

| Feature | Bootstrap | Bulma |
|---------|-----------|-------|
| Size | ~50KB | ~25KB |
| JavaScript | Required for components | None needed |
| Class naming | `btn btn-primary btn-lg` | `button is-primary is-large` |
| Grid system | 12-column with breakpoints | Flexible columns with `is-*` |
| Customization | Sass variables | Sass variables + CSS variables |
| Learning curve | Moderate | Lower |

Bulma provides a cleaner, more modern aesthetic with less overhead - perfect for a Thymeleaf-based application that already embraces server-side rendering.
