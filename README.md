# Eme

Eme is used to generate audio files, example sentences in the source language, and Anki Cards which can be helpful for language learning.

## Prerequisites

1. **Java 17+**: Make sure Java version 17 or higher is installed
2. **Docker**: Required for running the PostgreSQL database
3. **Google Cloud Console**: For translation and text-to-speech services
4. **OpenAI API**: For sentence generation using `gpt-4o-mini` model
5. **Anki Connect**: For creating Anki cards

## Running the app

Install anki connect addon on the Anki app that is running locally. Set the 
`ANKI_CONNECT_API_URL` environment variable from the Anki addon settings under config.
On ankiconnect config, add `http://localhost:8082` on `webCorsOriginList`.
You can also save this on `~/.bashrc` or `~/.zshrc`. Make sure it starts with `http://` or 
`https://`.

### 1. Start the Database

Start the PostgreSQL database using Docker Compose:
```sh
docker-compose up -d
```

This will create a PostgreSQL database with persistent storage for translations and sentences. The database schema and 100 Hindi words with example sentences will be automatically created using Flyway migrations when you start the application.

### 2. Environment Variables

Set the required environment variables:

```sh
# Anki Connect API URL
export ANKI_CONNECT_API_URL=[YOUR_ANKI_CONNECT_API_URL]

# OpenAI API Key for sentence generation
export OPENAI_API_KEY=[YOUR_OPENAI_API_KEY]
```

You can also save these in `~/.bashrc` or `~/.zshrc`.

### 3. Anki Connect Setup

Install the Anki Connect addon on your local Anki app. In the addon settings:
- Add `http://localhost:8082` to `webCorsOriginList`
- Make sure the API URL starts with `http://` or `https://`

### 4. Google Cloud Authentication

Authenticate with Google Cloud Console:
```sh
gcloud auth application-default set-quota-project [YOUR_PROJECT_ID]
gcloud auth application-default login
```

### 5. Start the Application

Run the application:
```sh
./gradlew bootRun
```

The app will be available at `http://localhost:8082`

## Features

- **Translation Storage**: Persistent database storage for Google Translate API results
- **Sentence Generation**: AI-powered sentence generation using OpenAI's `gpt-4o-mini`
- **Audio Generation**: Text-to-speech audio files for multiple languages including Hindi
- **Anki Integration**: Direct card creation with customizable templates
- **Storage Viewer**: Web interface to inspect stored translations and sentences at `/storage`

## Database

The application uses PostgreSQL for persistent storage of:
- Translation results from Google Translate API
- Generated sentences from OpenAI API

Data persists across application restarts and provides faster response times for repeated requests.