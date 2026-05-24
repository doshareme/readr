package labs.dx.core.data.db

import com.google.common.truth.Truth.assertThat
import labs.dx.core.domain.model.ReadingPosition
import org.junit.Test

class ReadingProgressEntityTest {
    @Test
    fun entity_roundTrips_withDomainModel() {
        val position = ReadingPosition(
            documentId = "doc-1",
            globalWordIndex = 42,
            pageIndex = 3,
            sentenceIndex = 8,
            paragraphIndex = 2,
            updatedAtEpochMillis = 999L
        )

        val entity = position.toEntity()

        assertThat(entity.toModel()).isEqualTo(position)
    }
}
