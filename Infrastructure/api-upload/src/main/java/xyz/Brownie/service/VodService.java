package xyz.Brownie.service;

import org.springframework.web.multipart.MultipartFile;
import xyz.Brownie.exception.EmptyContentException;

import java.util.List;

public interface VodService {

    String uploadVideo(MultipartFile file);

    void removeVideoByList(List videoIdList) throws EmptyContentException;

    void removeVideoById(String id);

    String getVideoPath(String id);
}
