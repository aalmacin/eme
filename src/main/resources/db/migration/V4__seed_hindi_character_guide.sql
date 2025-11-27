-- Seed Character Guide for Hindi
-- This maps Hindi sounds to memorable characters from anime, sports, and pop culture
-- Characters are chosen based on their name's starting sound matching the Hindi phoneme

-- Clear existing Hindi character guide entries (in case of re-run)
DELETE FROM character_guide WHERE language = 'hi';

-- Vowel sounds
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'a', 'Ash Ketchum', 'from Pokemon', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'aa', 'Arnold Schwarzenegger', 'the Hollywood action star', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'i', 'Itachi Uchiha', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ii', 'Eren Yeager', 'from Attack on Titan', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'u', 'Usopp', 'from One Piece', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'uu', 'Uchiha Sasuke', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'e', 'Edward Elric', 'from Fullmetal Alchemist', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ai', 'Aizen Sosuke', 'from Bleach', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'o', 'Orochimaru', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'au', 'Aang', 'from Avatar: The Last Airbender', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Consonant sounds - ka group
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'ka', 'Kakashi Hatake', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'kha', 'Khabib Nurmagomedov', 'the UFC fighter', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ga', 'Goku', 'from Dragon Ball', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'gha', 'Gaara', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'nga', 'Naruto Uzumaki', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Consonant sounds - cha group
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'cha', 'Charizard', 'from Pokemon', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'chha', 'Charlie Chaplin', 'the legendary silent film actor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ja', 'Jiraiya', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'jha', 'Jhené Aiko', 'the R&B singer', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'nya', 'Nyan Cat', 'the internet meme character', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Consonant sounds - ta group (dental)
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'ta', 'Takeshi Sawada', 'from Reborn!', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'tha', 'Thanos', 'from Marvel Avengers', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'da', 'Daredevil', 'from Marvel Comics', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'dha', 'Dhalsim', 'from Street Fighter', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'na', 'Naruto Uzumaki', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Consonant sounds - pa group
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'pa', 'Pikachu', 'from Pokemon', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'pha', 'Pharah', 'from Overwatch', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ba', 'Batman', 'from DC Comics', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'bha', 'Bhaichung Bhutia', 'the Indian football legend', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ma', 'Mario', 'from Super Mario Bros', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Consonant sounds - ya group
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'ya', 'Yagami Light', 'from Death Note', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ra', 'Ryu', 'from Street Fighter', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'la', 'Luffy', 'from One Piece', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'va', 'Vegeta', 'from Dragon Ball', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'wa', 'Wolverine', 'from X-Men', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Consonant sounds - sha group
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'sha', 'Shanks', 'from One Piece', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'shha', 'Shikamaru Nara', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'sa', 'Sasuke Uchiha', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ha', 'Harry Potter', 'from Harry Potter series', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Additional common starting sounds (2-letter combinations)
INSERT INTO character_guide (language, start_sound, character_name, character_context, created_at, updated_at)
VALUES
    ('hi', 'ki', 'Killua Zoldyck', 'from Hunter x Hunter', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ku', 'Kurama', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ke', 'Kenshin Himura', 'from Rurouni Kenshin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ko', 'Kobe Bryant', 'the NBA legend', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'gi', 'Gintoki Sakata', 'from Gintama', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'gu', 'Guts', 'from Berserk', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ge', 'Gengar', 'from Pokemon', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'go', 'Gohan', 'from Dragon Ball', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'chi', 'Chidori', 'the jutsu from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'chu', 'Chucky', 'from the horror movie series', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'che', 'Chewbacca', 'from Star Wars', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'cho', 'Chopper', 'from One Piece', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ji', 'Jiraya', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ju', 'Julius Caesar', 'the Roman emperor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'je', 'Jett', 'from Valorant', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'jo', 'Jotaro Kujo', 'from JoJo''s Bizarre Adventure', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ti', 'Tifa Lockhart', 'from Final Fantasy VII', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'tu', 'Tupac Shakur', 'the legendary rapper', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'te', 'Terminator', 'from the Terminator movies', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'to', 'Tony Stark', 'from Marvel Iron Man', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'di', 'Dio Brando', 'from JoJo''s Bizarre Adventure', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'du', 'Dumbledore', 'from Harry Potter', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'de', 'Deadpool', 'from Marvel Comics', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'do', 'Donkey Kong', 'from Nintendo games', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ni', 'Nico Robin', 'from One Piece', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'nu', 'Nuxia', 'from For Honor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ne', 'Neji Hyuga', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'no', 'Nobita Nobi', 'from Doraemon', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'pi', 'Piccolo', 'from Dragon Ball', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'pu', 'Punisher', 'from Marvel Comics', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'pe', 'Peach', 'from Super Mario Bros', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'po', 'Po', 'from Kung Fu Panda', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'bi', 'Beerus', 'from Dragon Ball Super', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'bu', 'Bulma', 'from Dragon Ball', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'be', 'Ben 10', 'from the cartoon series', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'bo', 'Bowser', 'from Super Mario Bros', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'mi', 'Mikasa Ackerman', 'from Attack on Titan', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'mu', 'Mulan', 'from the Disney movie', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'me', 'Megaman', 'from the Megaman video game series', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'mo', 'Monkey D. Luffy', 'from One Piece', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ri', 'Riku', 'from Kingdom Hearts', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ru', 'Rukia Kuchiki', 'from Bleach', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 're', 'Renji Abarai', 'from Bleach', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ro', 'Rock Lee', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'li', 'Link', 'from The Legend of Zelda', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'lu', 'Luke Skywalker', 'from Star Wars', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'le', 'Leonardo DiCaprio', 'the Hollywood actor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'lo', 'Loki', 'from Marvel Thor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'vi', 'Vivi', 'from One Piece', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'vu', 'Vulcan', 'from Marvel Comics', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 've', 'Venom', 'from Marvel Comics', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'vo', 'Voldemort', 'from Harry Potter', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'shi', 'Shikamaru Nara', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'shu', 'Shun Kazami', 'from Bakugan', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'she', 'Sherlock Holmes', 'the detective character', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'sho', 'Shoto Todoroki', 'from My Hero Academia', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'si', 'Simba', 'from The Lion King', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'su', 'Superman', 'from DC Comics', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'se', 'Sephiroth', 'from Final Fantasy VII', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'so', 'Sonic the Hedgehog', 'from the Sonic video game series', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'hi', 'Hinata Hyuga', 'from Naruto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'hu', 'Hulk', 'from Marvel Comics', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'he', 'Hercules', 'from Greek mythology and Disney', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('hi', 'ho', 'Homer Simpson', 'from The Simpsons', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
