# Build the APK using only your Android tablet

This project can be built in GitHub's cloud using GitHub Actions. You do not need Android Studio installed on the tablet. You do need a GitHub account and internet access.

## Important privacy note

Uploading this source code to a GitHub repository makes the source visible to anyone if you choose a public repository. This starter currently contains no passwords or API secrets, but choose **Private** if you prefer to keep the project private. GitHub Actions availability and usage limits depend on your account and repository.

## Steps

1. Open https://github.com in your tablet browser and sign in or create an account.
2. Create a new repository named `HookwayJobCamera`. Choose **Private** if offered. Do not add a README, licence, or .gitignore during creation.
3. In the repository, use **Add file → Upload files**. Upload the contents of this project folder, including the hidden `.github/workflows/build-apk.yml` file. The file must end up at `.github/workflows/build-apk.yml` in the repository. If the mobile browser cannot upload folders or hidden files, switch the browser to **Desktop site**; if that still fails, this method may need to wait until you have a computer.
4. Commit the uploaded files to the `main` branch.
5. Open the repository's **Actions** tab. If GitHub asks you to enable workflows, enable them.
6. Select **Build Hookway Job Camera APK** on the left and tap **Run workflow**, then **Run workflow** again. A push to `main` should also start a build automatically.
7. Open the running workflow. Wait for it to finish with a green check mark. Open the completed run and download the artifact named `HookwayJobCamera-debug-apk`.
8. The artifact downloads as a ZIP. Open your tablet's Downloads folder, extract it, then tap the `.apk` file inside. If Android asks, allow your browser or Files app to install unknown apps for this installation, then install it.

## If the build fails

Open the failed run and inspect the first red error in the **Build debug APK** step. Copy the error text (do not share account tokens or secrets) so it can be diagnosed.

## Current starter limitations

This only builds the existing starter. Google Drive folder creation and uploads are not implemented yet. Test the app only with non-critical sample jobs/photos until it has been properly tested. Keep backup copies of work photos.
