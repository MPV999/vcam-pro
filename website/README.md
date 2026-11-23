# VCAM PRO website

Static site, no build step. Served by GitHub Pages from this folder.

## Files

| File | Purpose |
|---|---|
| `index.html` | landing page: features, download, requirements, install, changelog |
| `styles.css` | dark theme, responsive |
| `config.js` | **edit this**: `owner` and `repo` of the GitHub repository |
| `app.js` | reads the newest GitHub release and wires the real APK URL + SHA-256 |
| `privacy.html` | privacy policy |
| `sdk.html` | SDK integration guide |
| `releases.json` | written by CI on every build (artifact sizes + SHA-256) |

## Configure

1. Open `config.js` and replace `OWNER` / `REPO` with the real repository.
2. Push the site to `main` with GitHub Pages set to **GitHub Actions**.
3. Publish a release with a `v*` tag; the download button resolves its APK asset.

Until a release exists, the button stays disabled and says so — there is no placeholder link.
