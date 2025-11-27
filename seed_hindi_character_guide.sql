-- Seed Character Guide for Hindi
-- This maps Hindi sounds to memorable characters from anime, sports, and pop culture
-- Characters are chosen based on their name's starting sound matching the Hindi phoneme

-- Clear existing Hindi character guide entries
DELETE FROM character_guide WHERE language = 'hi';

-- Vowel sounds
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'a', 'Ash Ketchum', 'from Pokemon', NOW(), NOW()),
    ('hi', 'aa', 'Arnold Schwarzenegger', 'the Hollywood action star', NOW(), NOW()),
    ('hi', 'i', 'Itachi Uchiha', 'from Naruto', NOW(), NOW()),
    ('hi', 'ii', 'Eren Yeager', 'from Attack on Titan', NOW(), NOW()),
    ('hi', 'u', 'Usopp', 'from One Piece', NOW(), NOW()),
    ('hi', 'uu', 'Uchiha Sasuke', 'from Naruto', NOW(), NOW()),
    ('hi', 'e', 'Edward Elric', 'from Fullmetal Alchemist', NOW(), NOW()),
    ('hi', 'ai', 'Aizen Sosuke', 'from Bleach', NOW(), NOW()),
    ('hi', 'o', 'Orochimaru', 'from Naruto', NOW(), NOW()),
    ('hi', 'au', 'Aang', 'from Avatar: The Last Airbender', NOW(), NOW());

-- Consonant sounds - ka group
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'ka', 'Kakashi Hatake', 'from Naruto', NOW(), NOW()),
    ('hi', 'kha', 'Khabib Nurmagomedov', 'the UFC fighter', NOW(), NOW()),
    ('hi', 'ga', 'Goku', 'from Dragon Ball', NOW(), NOW()),
    ('hi', 'gha', 'Gaara', 'from Naruto', NOW(), NOW()),
    ('hi', 'nga', 'Naruto Uzumaki', 'from Naruto', NOW(), NOW());

-- Consonant sounds - cha group
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'cha', 'Charizard', 'from Pokemon', NOW(), NOW()),
    ('hi', 'chha', 'Charlie Chaplin', 'the legendary silent film actor', NOW(), NOW()),
    ('hi', 'ja', 'Jiraiya', 'from Naruto', NOW(), NOW()),
    ('hi', 'jha', 'Jhené Aiko', 'the R&B singer', NOW(), NOW()),
    ('hi', 'nya', 'Nyan Cat', 'the internet meme character', NOW(), NOW());

-- Consonant sounds - ta group (dental)
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'ta', 'Takeshi Sawada', 'from Reborn!', NOW(), NOW()),
    ('hi', 'tha', 'Thanos', 'from Marvel Avengers', NOW(), NOW()),
    ('hi', 'da', 'Daredevil', 'from Marvel Comics', NOW(), NOW()),
    ('hi', 'dha', 'Dhalsim', 'from Street Fighter', NOW(), NOW()),
    ('hi', 'na', 'Naruto Uzumaki', 'from Naruto', NOW(), NOW());

-- Consonant sounds - pa group
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'pa', 'Pikachu', 'from Pokemon', NOW(), NOW()),
    ('hi', 'pha', 'Pharah', 'from Overwatch', NOW(), NOW()),
    ('hi', 'ba', 'Batman', 'from DC Comics', NOW(), NOW()),
    ('hi', 'bha', 'Bhaichung Bhutia', 'the Indian football legend', NOW(), NOW()),
    ('hi', 'ma', 'Mario', 'from Super Mario Bros', NOW(), NOW());

-- Consonant sounds - ya group
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'ya', 'Yagami Light', 'from Death Note', NOW(), NOW()),
    ('hi', 'ra', 'Ryu', 'from Street Fighter', NOW(), NOW()),
    ('hi', 'la', 'Luffy', 'from One Piece', NOW(), NOW()),
    ('hi', 'va', 'Vegeta', 'from Dragon Ball', NOW(), NOW()),
    ('hi', 'wa', 'Wolverine', 'from X-Men', NOW(), NOW());

-- Consonant sounds - sha group
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'sha', 'Shanks', 'from One Piece', NOW(), NOW()),
    ('hi', 'shha', 'Shikamaru Nara', 'from Naruto', NOW(), NOW()),
    ('hi', 'sa', 'Sasuke Uchiha', 'from Naruto', NOW(), NOW()),
    ('hi', 'ha', 'Harry Potter', 'from Harry Potter series', NOW(), NOW());

-- Additional common starting sounds
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'ki', 'Killua Zoldyck', 'from Hunter x Hunter', NOW(), NOW()),
    ('hi', 'ku', 'Kurama', 'from Naruto', NOW(), NOW()),
    ('hi', 'ke', 'Kenshin Himura', 'from Rurouni Kenshin', NOW(), NOW()),
    ('hi', 'ko', 'Kobe Bryant', 'the NBA legend', NOW(), NOW()),
    ('hi', 'gi', 'Gintoki Sakata', 'from Gintama', NOW(), NOW()),
    ('hi', 'gu', 'Guts', 'from Berserk', NOW(), NOW()),
    ('hi', 'ge', 'Gengar', 'from Pokemon', NOW(), NOW()),
    ('hi', 'go', 'Gohan', 'from Dragon Ball', NOW(), NOW()),
    ('hi', 'chi', 'Chidori', 'the jutsu from Naruto', NOW(), NOW()),
    ('hi', 'chu', 'Chucky', 'from the horror movie series', NOW(), NOW()),
    ('hi', 'che', 'Chewbacca', 'from Star Wars', NOW(), NOW()),
    ('hi', 'cho', 'Chopper', 'from One Piece', NOW(), NOW()),
    ('hi', 'ji', 'Jiraya', 'from Naruto', NOW(), NOW()),
    ('hi', 'ju', 'Julius Caesar', 'the Roman emperor', NOW(), NOW()),
    ('hi', 'je', 'Jett', 'from Valorant', NOW(), NOW()),
    ('hi', 'jo', 'Jotaro Kujo', 'from JoJo''s Bizarre Adventure', NOW(), NOW()),
    ('hi', 'ti', 'Tifa Lockhart', 'from Final Fantasy VII', NOW(), NOW()),
    ('hi', 'tu', 'Tupac Shakur', 'the legendary rapper', NOW(), NOW()),
    ('hi', 'te', 'Terminator', 'from the Terminator movies', NOW(), NOW()),
    ('hi', 'to', 'Tony Stark', 'from Marvel Iron Man', NOW(), NOW()),
    ('hi', 'di', 'Dio Brando', 'from JoJo''s Bizarre Adventure', NOW(), NOW()),
    ('hi', 'du', 'Dumbledore', 'from Harry Potter', NOW(), NOW()),
    ('hi', 'de', 'Deadpool', 'from Marvel Comics', NOW(), NOW()),
    ('hi', 'do', 'Donkey Kong', 'from Nintendo games', NOW(), NOW()),
    ('hi', 'ni', 'Nico Robin', 'from One Piece', NOW(), NOW()),
    ('hi', 'nu', 'Nuxia', 'from For Honor', NOW(), NOW()),
    ('hi', 'ne', 'Neji Hyuga', 'from Naruto', NOW(), NOW()),
    ('hi', 'no', 'Nobita Nobi', 'from Doraemon', NOW(), NOW()),
    ('hi', 'pi', 'Piccolo', 'from Dragon Ball', NOW(), NOW()),
    ('hi', 'pu', 'Punisher', 'from Marvel Comics', NOW(), NOW()),
    ('hi', 'pe', 'Peach', 'from Super Mario Bros', NOW(), NOW()),
    ('hi', 'po', 'Po', 'from Kung Fu Panda', NOW(), NOW()),
    ('hi', 'bi', 'Beerus', 'from Dragon Ball Super', NOW(), NOW()),
    ('hi', 'bu', 'Bulma', 'from Dragon Ball', NOW(), NOW()),
    ('hi', 'be', 'Ben 10', 'from the cartoon series', NOW(), NOW()),
    ('hi', 'bo', 'Bowser', 'from Super Mario Bros', NOW(), NOW()),
    ('hi', 'mi', 'Mikasa Ackerman', 'from Attack on Titan', NOW(), NOW()),
    ('hi', 'mu', 'Mulan', 'from the Disney movie', NOW(), NOW()),
    ('hi', 'me', 'Megaman', 'from the Megaman video game series', NOW(), NOW()),
    ('hi', 'mo', 'Monkey D. Luffy', 'from One Piece', NOW(), NOW()),
    ('hi', 'ri', 'Riku', 'from Kingdom Hearts', NOW(), NOW()),
    ('hi', 'ru', 'Rukia Kuchiki', 'from Bleach', NOW(), NOW()),
    ('hi', 're', 'Renji Abarai', 'from Bleach', NOW(), NOW()),
    ('hi', 'ro', 'Rock Lee', 'from Naruto', NOW(), NOW()),
    ('hi', 'li', 'Link', 'from The Legend of Zelda', NOW(), NOW()),
    ('hi', 'lu', 'Luke Skywalker', 'from Star Wars', NOW(), NOW()),
    ('hi', 'le', 'Leonardo DiCaprio', 'the Hollywood actor', NOW(), NOW()),
    ('hi', 'lo', 'Loki', 'from Marvel Thor', NOW(), NOW()),
    ('hi', 'vi', 'Vivi', 'from One Piece', NOW(), NOW()),
    ('hi', 'vu', 'Vulcan', 'from Marvel Comics', NOW(), NOW()),
    ('hi', 've', 'Venom', 'from Marvel Comics', NOW(), NOW()),
    ('hi', 'vo', 'Voldemort', 'from Harry Potter', NOW(), NOW()),
    ('hi', 'shi', 'Shikamaru Nara', 'from Naruto', NOW(), NOW()),
    ('hi', 'shu', 'Shun Kazami', 'from Bakugan', NOW(), NOW()),
    ('hi', 'she', 'Sherlock Holmes', 'the detective character', NOW(), NOW()),
    ('hi', 'sho', 'Shoto Todoroki', 'from My Hero Academia', NOW(), NOW()),
    ('hi', 'si', 'Simba', 'from The Lion King', NOW(), NOW()),
    ('hi', 'su', 'Superman', 'from DC Comics', NOW(), NOW()),
    ('hi', 'se', 'Sephiroth', 'from Final Fantasy VII', NOW(), NOW()),
    ('hi', 'so', 'Sonic the Hedgehog', 'from the Sonic video game series', NOW(), NOW()),
    ('hi', 'hi', 'Hinata Hyuga', 'from Naruto', NOW(), NOW()),
    ('hi', 'hu', 'Hulk', 'from Marvel Comics', NOW(), NOW()),
    ('hi', 'he', 'Hercules', 'from Greek mythology and Disney', NOW(), NOW()),
    ('hi', 'ho', 'Homer Simpson', 'from The Simpsons', NOW(), NOW());

-- Verify the count
SELECT COUNT(*) as total_characters FROM character_guide WHERE language = 'hi';
