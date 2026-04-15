package io.github.driftn2forty.chatsentinel.moderation;

import io.github.driftn2forty.chatsentinel.filter.AbbreviationExpander;
import io.github.driftn2forty.chatsentinel.filter.ChatNormalizer;
import io.github.driftn2forty.chatsentinel.filter.LocalFilterLayer;
import io.github.driftn2forty.chatsentinel.filter.ProfanityTrie;
import io.github.driftn2forty.chatsentinel.moderation.layer1.OpenAIModerationClient;
import io.github.driftn2forty.chatsentinel.util.DebugLogger;
import io.github.driftn2forty.chatsentinel.util.HttpUtil;
import io.github.driftn2forty.chatsentinel.util.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationPipelineTest {

    @TempDir
    Path tempDir;

    private ProfanityTrie trie;
    private LocalFilterLayer localFilter;
    private DebugLogger logger;

    @BeforeEach
    void setUp() {
        trie = new ProfanityTrie();
        trie.insert("badword");
        trie.insert("offensive");
        final ChatNormalizer normalizer = new ChatNormalizer();
        final AbbreviationExpander expander = new AbbreviationExpander(Map.of());
        localFilter = new LocalFilterLayer(trie, normalizer, expander, true, true);
        logger = new DebugLogger(Logger.getLogger("Test"), tempDir, false, false, false);
    }

    @Test
    void layer0CatchBlocksImmediately() {
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, null, null, false, false, 0.7, 3000, true, 1, 500, 5000, "block", logger);
        final ModerationPipeline.PipelineResult result = pipeline.process("this is a badword", null).join();
        assertNotNull(result);
        assertTrue(result.moderationResult().isFlagged());
        assertEquals(0, result.moderationResult().layer());
        assertEquals(ModerationResult.Verdict.WARN, result.moderationResult().verdict());
        assertNull(result.maskedMessage());
    }

    @Test
    void layer0CatchWithMaskMode() {
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, null, null, false, false, 0.7, 3000, true, 1, 500, 5000, "mask", logger);
        final ModerationPipeline.PipelineResult result = pipeline.process("this is a badword", null).join();
        assertTrue(result.moderationResult().isFlagged());
        assertNotNull(result.maskedMessage());
        assertTrue(result.isMasked());
    }

    @Test
    void cleanMessageAllowedWhenNoApiLayers() {
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, null, null, false, false, 0.7, 3000, true, 1, 500, 5000, "block", logger);
        final ModerationPipeline.PipelineResult result = pipeline.process("this is clean", null).join();
        assertFalse(result.moderationResult().isFlagged());
        assertEquals(ModerationResult.Verdict.ALLOW, result.moderationResult().verdict());
    }

    @Test
    void pipelineHandlesNullMessage() {
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, null, null, false, false, 0.7, 3000, true, 1, 500, 5000, "block", logger);
        final ModerationPipeline.PipelineResult result = pipeline.process(null, null).join();
        assertFalse(result.moderationResult().isFlagged());
    }

    @Test
    void pipelineHandlesEmptyMessage() {
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, null, null, false, false, 0.7, 3000, true, 1, 500, 5000, "block", logger);
        final ModerationPipeline.PipelineResult result = pipeline.process("", null).join();
        assertFalse(result.moderationResult().isFlagged());
    }

    @Test
    void maskedOutputReplacesOffensiveSpans() {
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, null, null, false, false, 0.7, 3000, true, 1, 500, 5000, "mask", logger);
        final ModerationPipeline.PipelineResult result = pipeline.process("say badword now", null).join();
        assertTrue(result.isMasked());
        assertFalse(result.maskedMessage().contains("badword"));
        assertTrue(result.maskedMessage().contains("*"));
    }

    @Test
    void failOpenReturnAllowOnLayer1Timeout() {
        final HttpUtil httpUtil = new HttpUtil();
        final RateLimiter rateLimiter = new RateLimiter(100);
        final OpenAIModerationClient layer1 = new OpenAIModerationClient("http://localhost:1", "fake-key", "test", 0.7, Map.of(), httpUtil, rateLimiter, logger);
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, layer1, null, true, false, 0.7, 500, true, 1, 100, 500, "block", logger);
        final ModerationPipeline.PipelineResult result = pipeline.process("clean message", null).join();
        assertFalse(result.moderationResult().isFlagged());
        httpUtil.shutdown();
    }

    @Test
    void failClosedReturnsEscalateOnError() {
        final HttpUtil httpUtil = new HttpUtil();
        final RateLimiter rateLimiter = new RateLimiter(100);
        final OpenAIModerationClient layer1 = new OpenAIModerationClient("http://localhost:1", "fake-key", "test", 0.7, Map.of(), httpUtil, rateLimiter, logger);
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, layer1, null, true, false, 0.7, 500, false, 1, 100, 500, "block", logger);
        final ModerationPipeline.PipelineResult result = pipeline.process("clean message", null).join();
        assertEquals(ModerationResult.Verdict.ESCALATE, result.moderationResult().verdict());
        httpUtil.shutdown();
    }

    @Test
    void multipleLayer0MatchesStillFlagged() {
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, null, null, false, false, 0.7, 3000, true, 1, 500, 5000, "block", logger);
        final ModerationPipeline.PipelineResult result = pipeline.process("badword and offensive content", null).join();
        assertTrue(result.moderationResult().isFlagged());
        assertEquals(0, result.moderationResult().layer());
    }

    @Test
    void responseTimeIsPopulated() {
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, null, null, false, false, 0.7, 3000, true, 1, 500, 5000, "block", logger);
        final ModerationPipeline.PipelineResult result = pipeline.process("say badword", null).join();
        assertTrue(result.moderationResult().responseTimeMs() >= 0);
    }
}
