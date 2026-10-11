package com.kindergarten.warehouse.service;
import com.kindergarten.warehouse.dto.request.BannerRequest;
import com.kindergarten.warehouse.entity.*;
import com.kindergarten.warehouse.mapper.BannerMapper;
import com.kindergarten.warehouse.repository.BannerRepository;
import com.kindergarten.warehouse.service.impl.BannerServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class BannerServiceTest {
    BannerRepository repo; MinioStorageService storage; BannerServiceImpl service;
    BannerRequest request; Banner banner;
    MockMultipartFile image=new MockMultipartFile("image","banner.png","image/png",new byte[]{1});
    @BeforeEach void setup() {
        repo=mock(BannerRepository.class);storage=mock(MinioStorageService.class);
        service=new BannerServiceImpl(repo,storage,new BannerMapper());
        request=new BannerRequest();request.setTitle("Banner test");request.setSubtitle("");
        request.setBgFrom("from-white");request.setBgTo("to-white");request.setVisibility(Visibility.PRIVATE);
        banner=Banner.builder().id(1L).title("Old").imageUrl("old-image").visibility(Visibility.PUBLIC).build();
        when(repo.findById(1L)).thenReturn(Optional.of(banner));when(repo.save(any())).thenAnswer(i->i.getArgument(0));
        when(storage.uploadFile(any(),eq("banners"))).thenReturn("new-image");
    }
    @Test void createAndEditRespectHiddenVisibility() {
        assertEquals(Visibility.PRIVATE,service.createBanner(request,image).getVisibility());
        assertEquals(Visibility.PRIVATE,service.updateBanner(1L,request,null).getResult().getVisibility());
    }
    @Test void uploadFailureKeepsOriginalImage() {
        when(storage.uploadFile(any(),eq("banners"))).thenThrow(new IllegalStateException("storage unavailable"));
        assertThrows(IllegalStateException.class,()->service.updateBanner(1L,request,image));
        assertEquals("old-image",banner.getImageUrl());verify(storage,never()).deleteFile(anyString());
    }
    @Test void replacementSavesNewReferenceBeforeCleaningUpOldImage() {
        service.updateBanner(1L,request,image);
        var order=inOrder(storage,repo);order.verify(storage).uploadFile(image,"banners");
        order.verify(repo).save(banner);order.verify(storage).deleteFile("old-image");
    }
}
