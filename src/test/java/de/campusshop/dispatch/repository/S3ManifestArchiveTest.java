package de.campusshop.dispatch.repository;

import de.campusshop.dispatch.config.DispatchProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class S3ManifestArchiveTest {
    @Mock S3Client s3;
    S3ManifestArchive archive;
    @BeforeEach void setUp() {
        archive = new S3ManifestArchive(s3, new DispatchProperties(new DispatchProperties.Archive("custom-bucket"), null, null));
    }
    @Test void archivesTheOriginalBytesAndContentType() throws Exception {
        archive.archive(item().manifestKey(), raw());
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("custom-bucket");
        assertThat(request.getValue().key()).isEqualTo(item().manifestKey());
        assertThat(request.getValue().contentType()).isEqualTo("application/json");
        try (var input = body.getValue().contentStreamProvider().newStream()) {
            assertThat(input.readAllBytes()).isEqualTo(raw());
        }
    }
    @Test void retrievesVerbatimBytes() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), raw()));
        assertThat(archive.fetch(item().manifestKey())).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(raw()));
        var request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObjectAsBytes(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("custom-bucket");
        assertThat(request.getValue().key()).isEqualTo(item().manifestKey());
    }
    @Test void missingKeyReturnsEmpty() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("missing").build());
        assertThat(archive.fetch(item().manifestKey())).isEmpty();
    }
    @Test void storageFailureIsNotMisreportedAsMissing() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(500).build());
        assertThatThrownBy(() -> archive.fetch(item().manifestKey())).isInstanceOf(S3Exception.class);
    }
    @Test void removesTheConfiguredObject() {
        archive.remove(item().manifestKey());
        var request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("custom-bucket");
        assertThat(request.getValue().key()).isEqualTo(item().manifestKey());
    }
}
