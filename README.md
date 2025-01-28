# Eme

Eme is used to generate audio files and Anki Cards which can be helpful for language learning.

## Running the app

Install anki connect addon on the Anki app that is running locally. Set the `ANKI_CONNECT_API_URL` environment variable from the Anki addon settings under config.
You can also save this on ~/.bashrc or ~/.zshrc. Make sure it starts with http:// or https://.
```sh
export ANKI_CONNECT_API_URL=[MY_CONNECT_API_URL]
```

Authenticate with google console.

```sh
gcloud auth application-default set-quota-project [MY_PROJECT_ID]
gcloud auth application-default login
```

Run `./gradlew bootRun` to start running it locally. Make sure java is version 17 or higher.
```sh
./gradlew bootRun
```