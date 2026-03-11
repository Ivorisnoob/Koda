const express = require("express");
const cors = require("cors");
const ytdl = require("@distube/ytdl-core");
const yts = require("yt-search");

const app = express();
app.use(cors());

// --- ENDPOINTS ---

// Search Endpoint
app.get("/search", async (req, res) => {
    try {
        const query = req.query.q;
        if (!query) return res.status(400).json({ error: "Missing query parameter" });

        const result = await yts(query);
        const videos = result.videos.slice(0, 20).map(v => ({
            id: v.videoId,
            title: v.title,
            artist: v.author.name,
            duration: v.timestamp,
            durationSec: v.seconds,
            art: v.thumbnail,
            hasVideo: true
        }));

        res.json(videos);
    } catch (err) {
        console.error("Search Error:", err);
        res.status(500).json({ error: "Error searching videos" });
    }
});

// Trending Endpoint (yt-search doesn't have a strict trending, so we use a popular search)
app.get("/trending", async (req, res) => {
    try {
        const query = req.query.filter === 'video' ? 'trending viral videos' : 'trending popular music hits official';
        const result = await yts(query);
        const videos = result.videos.slice(0, 20).map(v => ({
            id: v.videoId,
            title: v.title,
            artist: v.author.name,
            duration: v.timestamp,
            durationSec: v.seconds,
            art: v.thumbnail,
            hasVideo: true
        }));

        res.json(videos);
    } catch (err) {
        console.error("Trending Error:", err);
        res.status(500).json({ error: "Error loading trending" });
    }
});

// Audio Stream Endpoint
app.get("/audio", async (req, res) => {
    try {
        const id = req.query.id;
        if (!id) return res.status(400).send("Invalid Video ID");

        const url = `https://www.youtube.com/watch?v=${id}`;

        if (!ytdl.validateURL(url)) {
            return res.status(400).send("Invalid YouTube URL");
        }

        const info = await ytdl.getInfo(url);
        // Find best audio format
        const audioFormat = ytdl.chooseFormat(info.formats, { filter: 'audioonly', quality: 'highestaudio' });

        if (!audioFormat) return res.status(404).send("No audio format found");

        res.json({
            url: audioFormat.url,
            mimeType: audioFormat.mimeType,
            title: info.videoDetails.title
        });

    } catch (err) {
        console.error("Audio Extractor Error:", err);
        res.status(500).send("Error extracting audio");
    }
});

// Video Stream Endpoint
app.get("/video", async (req, res) => {
    try {
        const id = req.query.id;
        if (!id) return res.status(400).send("Invalid Video ID");

        const url = `https://www.youtube.com/watch?v=${id}`;

        if (!ytdl.validateURL(url)) {
            return res.status(400).send("Invalid YouTube URL");
        }

        const info = await ytdl.getInfo(url);
        // Find best format that has both video and audio (MP4)
        const videoFormat = ytdl.chooseFormat(info.formats, { filter: 'audioandvideo', quality: 'highest' });

        if (!videoFormat) return res.status(404).send("No video format found");

        res.json({
            url: videoFormat.url,
            mimeType: videoFormat.mimeType,
            title: info.videoDetails.title
        });

    } catch (err) {
        console.error("Video Extractor Error:", err);
        res.status(500).send("Error extracting video");
    }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Backend Server running on port ${PORT}`);
});
