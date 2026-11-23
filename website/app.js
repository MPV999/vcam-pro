(function () {
    "use strict";

    var config = window.VCAM_CONFIG || { owner: "OWNER", repo: "REPO" };
    var configured = config.owner !== "OWNER" && config.repo !== "REPO";

    var button = document.getElementById("download-btn");
    var meta = document.getElementById("release-meta");
    var versionFact = document.getElementById("fact-version");
    var sizeFact = document.getElementById("fact-size");
    var shaFact = document.getElementById("fact-sha");
    var changelog = document.getElementById("changelog-body");
    var releasesLink = document.getElementById("releases-link");

    function humanSize(bytes) {
        if (!bytes) return "—";
        var units = ["B", "KB", "MB", "GB"];
        var value = bytes;
        var index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index += 1;
        }
        return value.toFixed(index === 0 ? 0 : 1) + " " + units[index];
    }

    function setDisabled(text) {
        if (!button) return;
        button.textContent = text;
        button.classList.add("disabled");
        button.setAttribute("aria-disabled", "true");
        button.removeAttribute("href");
    }

    function renderRelease(release) {
        var assets = release.assets || [];
        // Never offer an unsigned APK: Android refuses to install it.
        var installable = assets.filter(function (asset) {
            if (!asset.name) return false;
            var name = asset.name.toLowerCase();
            return name.endsWith(".apk") && name.indexOf("unsigned") === -1;
        });
        var apk = installable.filter(function (asset) {
            return asset.name.toLowerCase().indexOf("vcam-pro") === 0;
        })[0] || installable[0];
        var sums = assets.filter(function (asset) {
            return asset.name && /sha256/i.test(asset.name);
        })[0];

        if (!apk) {
            setDisabled("This release has no APK yet");
            if (meta) meta.textContent = "Release " + release.tag_name + " contains no APK asset.";
            return;
        }

        if (button) {
            button.textContent = "Download APK · " + (release.name || release.tag_name);
            button.href = apk.browser_download_url;
            button.classList.remove("disabled");
            button.removeAttribute("aria-disabled");
        }
        if (versionFact) versionFact.textContent = release.name || release.tag_name;
        if (sizeFact) sizeFact.textContent = humanSize(apk.size);
        if (meta) {
            meta.textContent = "Published " + new Date(release.published_at).toLocaleDateString() +
                " · " + apk.name;
        }

        if (sums) {
            fetch(sums.browser_download_url)
                .then(function (response) { return response.ok ? response.text() : ""; })
                .then(function (text) {
                    var match = text.match(/([a-f0-9]{64})/i);
                    if (match && shaFact) {
                        shaFact.textContent = match[1];
                    }
                })
                .catch(function () { /* the checksum file is optional */ });
        }

        if (changelog && release.body) {
            changelog.innerHTML = "";
            var heading = document.createElement("p");
            heading.className = "muted";
            heading.textContent = release.name || release.tag_name;
            changelog.appendChild(heading);
            var pre = document.createElement("pre");
            pre.textContent = release.body;
            changelog.appendChild(pre);
        }
    }

    if (releasesLink && configured) {
        releasesLink.href = "https://github.com/" + config.owner + "/" + config.repo + "/releases";
    }

    if (!configured) {
        setDisabled("Release not published yet");
        if (meta) {
            meta.textContent = "Set owner and repo in website/config.js once the repository exists.";
        }
        return;
    }

    var url = "https://api.github.com/repos/" + config.owner + "/" + config.repo + "/releases/latest";

    fetch(url, { headers: { Accept: "application/vnd.github+json" } })
        .then(function (response) {
            if (response.status === 404) throw new Error("no releases yet");
            if (!response.ok) throw new Error("GitHub API responded " + response.status);
            return response.json();
        })
        .then(renderRelease)
        .catch(function (error) {
            setDisabled("No APK published yet");
            if (meta) {
                meta.textContent = "Could not read the latest release (" + error.message +
                    "). Check the releases page instead.";
            }
        });
})();
