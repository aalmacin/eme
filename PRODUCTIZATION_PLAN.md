# Productization Plan: Anki Card Management

## Current Implementation

The application currently uses AnkiConnect to create Anki decks and notes:
- `AnkiConnectService.java` - Makes HTTP requests to local AnkiConnect instance
- `AnkiNoteCreatorService.java` - Creates notes with front/back fields
- Requires users to have Anki Desktop running with AnkiConnect add-on installed

## Problem Statement

AnkiConnect dependency creates barriers for productization:
- Requires desktop application running locally
- Complex setup for end users
- Not suitable for web-based SaaS model
- Limits ability to scale and sell subscriptions

## Alternative Approaches

### 1. Generate .apkg Files (RECOMMENDED)

**Description:** Generate Anki deck packages (.apkg files) that users can import directly into Anki.

**Technical Details:**
- .apkg format is a ZIP container containing:
  - SQLite database (collection.anki2)
  - Media files
  - Metadata
- Format specification is publicly documented
- Can be generated without Anki libraries

**Pros:**
- No desktop app running required
- Users download and import at their convenience
- Works with Anki Desktop, AnkiMobile, AnkiDroid
- Better for web-based SaaS products
- No local dependencies
- Clean separation from Anki ecosystem

**Cons:**
- Users must manually import files
- No automatic sync to existing decks
- Need to implement .apkg generation logic

**Implementation Options:**
- Use existing Java libraries (if available)
- Build custom .apkg generator
- SQLite JDBC for database creation
- Java ZIP utilities for packaging

**License Considerations:**
- Generating .apkg based on public format spec is legal (reverse engineering for interoperability)
- No GPL/AGPL contamination

### 2. Direct SQLite Manipulation

**Description:** Access Anki's collection.anki2 database directly on user's file system.

**Pros:**
- Complete control over data
- No intermediary service
- Fast operations

**Cons:**
- **HIGH RISK**: Anki schema changes break application
- Must handle Anki's internal logic (scheduling algorithms, ID generation, checksums)
- Requires local file system access to user's Anki directory
- Complex scheduling algorithm implementation
- Database corruption risks
- **NOT RECOMMENDED FOR PRODUCTION**

**Verdict:** Too risky for commercial product

### 3. Use Anki Python Libraries

**Description:** Import and use Anki's official Python libraries (pylib, anki package).

**Pros:**
- Official implementation
- Handles all internal logic correctly
- Proper scheduling, ID generation, etc.

**Cons:**
- **AGPL License** - requires entire application to be open source
- Python dependency (current stack is Java/Spring Boot)
- Still requires local Anki installation for some operations
- Complex integration with Spring Boot architecture
- JNI/process communication overhead
- License incompatible with commercial closed-source product

**Verdict:** License restrictions make this unsuitable

### 4. Build Custom Spaced Repetition System

**Description:** Create independent SRS algorithm and card management system.

**Pros:**
- Complete ownership and control
- No licensing concerns
- Optimize for specific use case
- Better for subscription model
- Can build unique features
- Full branding control

**Cons:**
- Significant development effort
- Users can't leverage existing Anki ecosystem
- Must build mobile apps separately
- Need to replicate proven SRS algorithms (SM-2, FSRS)
- User adoption barrier (leaving Anki)

**Long-term Potential:**
- Build competitive advantage
- Own the entire user experience
- No dependency on external ecosystem

## Licensing Concerns

### Anki License: AGPL-3.0
- If you use Anki's code libraries, entire application must be open source
- Derivative works must also be AGPL
- Incompatible with commercial closed-source SaaS

### AnkiConnect License: GPL
- Similar copyleft restrictions
- Using the code requires open-sourcing your application

### Safe Approaches:
- Generating .apkg files based on public format specification (reverse engineering for interoperability)
- Implementing SRS algorithms independently (algorithms aren't copyrightable)
- Clean-room implementation without using Anki source code

## Business Model Considerations

### Current User Workflow (AnkiConnect)
```
User → Install Anki Desktop → Install AnkiConnect → Keep Anki running → Use our app
```
**Issues:** Too many friction points for paid product

### Proposed User Workflows

#### Option A: .apkg Generation + Optional AnkiConnect
```
Power users (Anki Desktop running) → AnkiConnect (direct sync)
Regular users → Download .apkg files (manual import)
Mobile users → Download .apkg, import to AnkiDroid/AnkiMobile
```

#### Option B: Hybrid System
```
App manages cards in proprietary database
↓
Branch 1: Export to .apkg for Anki users
Branch 2: Use web/mobile interface with built-in SRS
```

#### Option C: Full Independence
```
App provides complete SRS solution
Optional: Export to .apkg for migration/backup
```

## Recommended Strategy

### Phase 1: Short-term (Immediate Productization)
1. **Implement .apkg file generation** in Java
   - Build custom generator using SQLite JDBC
   - Support basic card types (Basic, Basic with Reversed)
   - Package as downloadable files

2. **Keep AnkiConnect as optional feature**
   - Power users can enable direct sync
   - Clearly marked as "advanced" feature
   - Not required for core functionality

3. **Benefits:**
   - Quick to market
   - No licensing issues
   - Works for most users
   - Maintains Anki ecosystem compatibility

### Phase 2: Medium-term (Enhanced Experience)
1. **Add web-based review interface**
   - Users can review cards in browser
   - Implement basic SRS algorithm (SM-2)
   - Still export to Anki for mobile review

2. **Enhance .apkg generation**
   - Support more card types
   - Custom templates
   - Media files (images, audio)
   - Tags and deck hierarchies

### Phase 3: Long-term (Full Independence)
1. **Build native mobile apps**
   - iOS and Android applications
   - Full SRS functionality
   - Sync with web platform

2. **Advanced SRS features**
   - Implement FSRS algorithm (modern alternative to SM-2)
   - Analytics and learning insights
   - Personalized scheduling

3. **Maintain .apkg export**
   - Users can migrate data out
   - Backup functionality
   - Trust signal for users

## Technical Implementation Plan

### .apkg File Generation Architecture

```
Current Database (PostgreSQL)
↓
AnkiPackageGenerator Service
  ├─ Create SQLite database
  ├─ Generate card IDs (timestamp-based)
  ├─ Insert notes and cards
  ├─ Set up deck structure
  ├─ Calculate initial scheduling
  └─ Package as ZIP file
↓
Downloadable .apkg file
```

### Database Schema Mapping

**Our Schema → Anki Schema**
```
TranslationSessionEntity → Deck
CardItem → Note + Card
AnkiFormatEntity → Note Type (Model)
```

### Required Components

1. **SQLite Database Creator**
   - Use sqlite-jdbc library
   - Implement Anki schema (col, notes, cards, graves, revlog)
   - Handle foreign key relationships

2. **ID Generation**
   - Timestamp-based IDs (milliseconds since epoch)
   - Ensure uniqueness across notes and cards

3. **Note Type (Model) Handler**
   - Define templates (front/back HTML)
   - CSS styling
   - Field definitions

4. **Deck Configuration**
   - Default deck options
   - New cards per day limits
   - Review settings

5. **ZIP Packaging**
   - Use Java's built-in ZIP utilities
   - Include collection.anki2
   - Include media files (if any)
   - Proper file permissions

### Integration Points

**Existing Code:**
- `AnkiCardBuilderService.java` - Already builds card data
- `AnkiFormatEntity.java` - Defines card structure
- `ZipFileGenerator.java` - Utility for ZIP creation

**New Code Needed:**
- `AnkiPackageGenerator.java` - Main generation service
- `AnkiDatabaseBuilder.java` - SQLite database creation
- `AnkiSchemaConstants.java` - Anki schema definitions
- Update controllers to offer .apkg download

## Risk Assessment

### High Priority Risks

1. **Anki Format Changes**
   - **Risk:** Anki updates schema, breaking .apkg generation
   - **Mitigation:** Monitor Anki releases, test generated files, maintain compatibility layer

2. **License Violations**
   - **Risk:** Accidentally using AGPL code
   - **Mitigation:** Clean-room implementation, legal review, document sources

3. **User Adoption**
   - **Risk:** Users prefer direct AnkiConnect integration
   - **Mitigation:** Support both methods, clear documentation, smooth UX

### Medium Priority Risks

1. **SRS Algorithm Complexity**
   - **Risk:** Custom implementation performs poorly
   - **Mitigation:** Start with proven SM-2, extensive testing, user feedback

2. **Mobile Development Costs**
   - **Risk:** Native apps are expensive to maintain
   - **Mitigation:** Phase 3 only, validate market first, consider cross-platform frameworks

## Success Metrics

### Phase 1 Success Criteria
- Generate valid .apkg files importable to Anki
- 90%+ of users can successfully import files
- No licensing issues
- Maintain current AnkiConnect functionality as optional

### Phase 2 Success Criteria
- 50%+ of users try web-based review
- Retention rate improves with integrated experience
- Positive user feedback on SRS implementation

### Phase 3 Success Criteria
- Mobile apps achieve 4+ star ratings
- Reduced dependency on Anki ecosystem
- Subscription revenue growth from mobile users

## Next Steps

1. **Research existing Java libraries** for .apkg generation
2. **Prototype .apkg generator** with basic functionality
3. **Test generated files** with Anki Desktop, AnkiDroid, AnkiMobile
4. **Update database schema** to support both export formats
5. **Implement download endpoints** for .apkg files
6. **Document user workflow** for importing .apkg files
7. **Add analytics** to track .apkg downloads vs AnkiConnect usage

## Resources

### Documentation
- [Anki Database Structure](https://github.com/ankidroid/Anki-Android/wiki/Database-Structure)
- [Anki .apkg Format](https://github.com/ankidroid/Anki-Android)
- [Anki Manual](https://docs.ankiweb.net/)

### Libraries
- sqlite-jdbc: Java SQLite database connectivity
- Apache Commons Compress: ZIP file creation
- Consider: genanki alternatives for Java (if available)

### Community
- Anki forums for format questions
- AnkiDroid developers for technical details
- SRS algorithm papers (Wozniak's SM-2, FSRS)

## Conclusion

**Recommended path forward:**
1. Implement .apkg file generation (Phase 1)
2. Keep AnkiConnect as optional advanced feature
3. Evaluate custom SRS system after market validation
4. Maintain flexibility to support both ecosystems

This approach minimizes risk, avoids licensing issues, and provides immediate path to productization while keeping options open for future independence from the Anki ecosystem.
