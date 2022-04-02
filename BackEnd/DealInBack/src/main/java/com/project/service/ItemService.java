package com.project.service;

import com.project.domain.categories.Categories;
import com.project.domain.categories.CategoriesRepository;
import com.project.domain.itemphotos.ItemPhotos;
import com.project.domain.itemphotos.ItemPhotosRepository;
import com.project.domain.items.*;
import com.project.domain.useritems.UserItems;
import com.project.domain.useritems.UserItemsRepository;
import com.project.domain.useritems.UserItemsReqDto;
import com.project.domain.users.Users;
import com.project.domain.users.UsersRepository;
import com.project.domain.userswish.UsersWishRepository;
import com.project.domain.userswish.UsersWishReqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ItemService {

    private final UsersRepository usersRepository;
    private final ItemsRepository itemsRepository;
    private final CategoriesRepository categoriesRepository;
    private final ItemPhotosRepository itemPhotosRepository;
    private final UsersWishRepository usersWishRepository;
    private final UserItemsRepository userItemsRepository;

    public void createItem(Integer userId, ItemSaveReqDto itemSaveReqDto) {
        Users user = usersRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자 정보가 존재하지 않습니다."));
        Categories categories = categoriesRepository.findByName(itemSaveReqDto.getCategoryName());

        Items item = itemSaveReqDto.toEntity(user, categories);
        Items savedItem = itemsRepository.save(item);

        List<ItemPhotos> itemPhotos = photosToItemPhotos(itemSaveReqDto.getPhotos(), savedItem);
        itemPhotos.forEach(itemPhotosRepository::save);
    }

    public List<ItemResDto> getItems(Users user, int page) {
        List<ItemResDto> itemResDtos = new ArrayList<>();
        Page<Items> itemsList = itemsRepository.findAll(PageRequest.of(page, 10));
        for (Items item : itemsList) {
            String photoUrl = itemPhotosRepository.findFirstByItemsId(item.getId()).getPhotoUrl();
            itemResDtos.add(new ItemResDto(item, photoUrl, false));
        }

        if (user != null) {
            for (ItemResDto dto : itemResDtos) {
                if (usersWishRepository.existsByUsersIdAndItemsId(user.getId(), dto.getItemsId())) {
                    dto.setWish(true);
                }
            }
        }

        return itemResDtos;
    }

//    public ItemDetailResDto getDetailItems(Users user, int itemsId) {
//
//    }

    public void setWishItem(Users user, UsersWishReqDto usersWishReqDto) {
        Items item = itemsRepository.findById(usersWishReqDto.getItemId()).orElseThrow(() -> new IllegalArgumentException("상품 정보가 존재하지 않습니다."));

        // 찜이 false이면 새로 등록
        if (!usersWishReqDto.getWish())
            usersWishRepository.save(usersWishReqDto.toEntity(item, user));
        // 찜이 true이면 해제
        else
            usersWishRepository.deleteByUsersIdAndItemsId(user.getId(), item.getId());
    }

    public Boolean isBidItem(Users user, Integer itemId) throws Exception {
        if (userItemsRepository.existsByUsersIdAndItemsIdAndIsPurchase(user.getId(), itemId, true))
            throw new Exception("이미 구매한 상품입니다.");

        return userItemsRepository.existsByUsersIdAndItemsIdAndIsPurchase(user.getId(), itemId, false);
    }

    public void bidItem(int flag, Users user, UserItemsReqDto userItemsReqDto) {
        Items item = itemsRepository.findById(userItemsReqDto.getItemId()).orElseThrow(() -> new IllegalArgumentException("상품 정보가 존재하지 않습니다."));
        
        // 첫 입찰이면
        if (flag == 1)
            userItemsRepository.save(userItemsReqDto.toEntity(item, user));
        // 두 번째 입찰 이상이면
        else {
            UserItems userItems = userItemsRepository.findLastByUsersIdAndItemsIdAndIsPurchase(user.getId(), item.getId(), false);
            userItems.rebidItem(userItemsReqDto.getPrice(), userItemsReqDto.getDealDate());
            userItemsRepository.save(userItemsReqDto.toEntity(item, user));
        }

        Integer newPoint = Integer.parseInt(String.format("%.0f", userItemsReqDto.getPrice() * 0.9));
        user.setPoint(newPoint);
        usersRepository.save(user);
    }



    public List<ItemPhotos> photosToItemPhotos(List<String> photos, Items item) {
        List<ItemPhotos> itemPhotos = new ArrayList<>();
        for (String photoUrl : photos) {
            ItemPhotos photo = ItemPhotos.builder()
                    .items(item)
                    .photoUrl(photoUrl)
                    .build();
            itemPhotos.add(photo);
        }

        return itemPhotos;
    }


}
