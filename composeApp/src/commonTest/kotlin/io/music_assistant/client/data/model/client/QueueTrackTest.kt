package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.AudioFidelity
import io.music_assistant.client.data.model.server.AudioOutputDetails
import io.music_assistant.client.data.model.server.AudioProcessingChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueTrackTest {
    @Test
    fun `qualityTier maps input quality values to tiers`() {
        assertNull(
            queueTrackWithChain(null).qualityTier,
        )

        assertNull(
            queueTrackWithChain(
                AudioProcessingChain(inputFidelity = AudioFidelity(quality = null)),
            ).qualityTier,
        )

        assertNull(
            queueTrackWithChain(
                AudioProcessingChain(inputFidelity = AudioFidelity(quality = "blah")),
            ).qualityTier,
        )

        assertNull(
            queueTrackWithChain(
                AudioProcessingChain(
                    inputFidelity = AudioFidelity(quality = AudioFidelity.QUALITY_UNKNOWN),
                ),
            ).qualityTier,
        )

        assertEquals(
            QualityTier.LQ,
            queueTrackWithChain(
                AudioProcessingChain(
                    inputFidelity = AudioFidelity(quality = AudioFidelity.QUALITY_LOW),
                ),
            ).qualityTier,
        )

        assertEquals(
            QualityTier.SQ,
            queueTrackWithChain(
                AudioProcessingChain(
                    inputFidelity = AudioFidelity(quality = AudioFidelity.QUALITY_STANDARD),
                ),
            ).qualityTier,
        )

        assertEquals(
            QualityTier.HQ,
            queueTrackWithChain(
                AudioProcessingChain(
                    inputFidelity = AudioFidelity(quality = AudioFidelity.QUALITY_LOSSLESS),
                ),
            ).qualityTier,
        )

        assertEquals(
            QualityTier.HR,
            queueTrackWithChain(
                AudioProcessingChain(
                    inputFidelity = AudioFidelity(quality = AudioFidelity.QUALITY_HI_RES),
                ),
            ).qualityTier,
        )
    }

    @Test
    fun `if output quality is worse than input uses output`() {
        assertEquals(
            QualityTier.LQ,
            queueTrackWithChain(
                AudioProcessingChain(
                    inputFidelity = AudioFidelity(quality = AudioFidelity.QUALITY_HI_RES),
                    outputs = listOf(
                        AudioOutputDetails(
                            fidelity = AudioFidelity(
                                quality = AudioFidelity.QUALITY_LOW,
                            ),
                        ),
                    ),
                ),
            ).qualityTier,
        )
    }

    @Test
    fun `if any output quality is worse than input uses the lowest one`() {
        assertEquals(
            QualityTier.LQ,
            queueTrackWithChain(
                AudioProcessingChain(
                    inputFidelity = AudioFidelity(quality = AudioFidelity.QUALITY_HI_RES),
                    outputs = listOf(
                        AudioOutputDetails(
                            fidelity = AudioFidelity(
                                quality = AudioFidelity.QUALITY_STANDARD,
                            ),
                        ),
                        AudioOutputDetails(
                            fidelity = AudioFidelity(
                                quality = AudioFidelity.QUALITY_LOW,
                            ),
                        ),
                    ),
                ),
            ).qualityTier,
        )
    }

    private fun queueTrackWithChain(audioProcessingChain: AudioProcessingChain?): QueueTrack {
        return QueueTrack(
            id = "id",
            track = AppMediaItemFixtures.track(),
            isPlayable = true,
            format = null,
            provider = null,
            audioProcessingChain = audioProcessingChain,
        )
    }
}
