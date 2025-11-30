package sunshine_dental_care.services.huybro_products.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.huybro_products.*;
import sunshine_dental_care.entities.huybro_products.*;
import sunshine_dental_care.repositories.huybro_products.ProductImageRepository;
import sunshine_dental_care.repositories.huybro_products.ProductRepository;
import sunshine_dental_care.repositories.huybro_products.ProductTypeRepository;
import sunshine_dental_care.repositories.huybro_products.ProductsProductTypeRepository;
import sunshine_dental_care.services.huybro_products.interfaces.ProductService;
import sunshine_dental_care.utils.huybro_utils.format.FormatTypeProduct;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductsProductTypeRepository productsProductTypeRepository;
    private final ProductTypeRepository productTypeRepository;

    public ProductServiceImpl(ProductRepository productRepository,
                              ProductImageRepository productImageRepository,
                              ProductsProductTypeRepository productsProductTypeRepository,
                              ProductTypeRepository productTypeRepository) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.productsProductTypeRepository = productsProductTypeRepository;
        this.productTypeRepository = productTypeRepository;
    }

    // Hàm map từ Entity → DTO
    private ProductDto mapToDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.setProductId(product.getId());
        dto.setSku(product.getSku());
        dto.setProductName(product.getProductName());
        dto.setBrand(product.getBrand());
        dto.setProductDescription(product.getProductDescription());
        dto.setUnit(product.getUnit());
        dto.setDefaultRetailPrice(product.getDefaultRetailPrice());
        dto.setCurrency(product.getCurrency());
        dto.setIsTaxable(product.getIsTaxable());
        dto.setTaxCode(product.getTaxCode());
        dto.setIsActive(product.getIsActive());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        // Lấy danh sách ảnh
        var images = productImageRepository.findByProduct_IdOrderByImageOrderAsc(product.getId())
                .stream().map(img -> {
                    var it = new ProductImageDto();
                    it.setImageId(img.getId());
                    it.setImageUrl(img.getImageUrl());
                    it.setImageOrder(img.getImageOrder());
                    return it;
                }).toList();
        dto.setImage(images);

        // Lấy danh sách loại
        List<String> typeNames = productsProductTypeRepository.findByProduct_Id(product.getId())
                .stream()
                .map(ppt -> ppt.getType().getTypeName())
                .collect(Collectors.toList());
        dto.setTypeNames(typeNames);

        return dto;
    }

    @Override
    public List<ProductDto> findAllProducts() {
        List<Product> products = productRepository.findAll();

        return products.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    public ProductDto findById(Integer id) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) {
            return null;
        }
        return mapToDto(productOpt.get());
    }
    @Override
    public Page<ProductDto> search(ProductFilterDto filter, Pageable pageable) {
        String keyword = (filter != null && filter.getKeyword() != null && !filter.getKeyword().isBlank())
                ? filter.getKeyword().trim()
                : null;

        boolean brandEnabled = filter != null && filter.getBrands() != null && !filter.getBrands().isEmpty();
        boolean typeEnabled  = filter != null && filter.getTypes()  != null && !filter.getTypes().isEmpty();

        var brands = brandEnabled ? filter.getBrands() : List.<String>of();
        var types  = typeEnabled  ? filter.getTypes()  : List.<String>of();

        var page = productRepository.searchProducts(
                keyword,
                brands, brandEnabled,
                types,  typeEnabled,
                filter != null ? filter.getMinPrice() : null,
                filter != null ? filter.getMaxPrice() : null,
                filter != null ? filter.getActive()   : null,
                pageable
        );
        return page.map(this::mapToDto);
    }

    @Override
    @Transactional
    public ProductDto createProduct(ProductCreateDto dto) {
        LocalDateTime now = LocalDateTime.now();

        Product product = new Product();
        product.setSku(dto.getSku().trim());
        product.setProductName(dto.getProductName().trim());
        product.setBrand(dto.getBrand().trim());
        product.setProductDescription(dto.getProductDescription().trim());
        product.setUnit(dto.getUnit());
        product.setDefaultRetailPrice(dto.getDefaultRetailPrice());

        String currency = dto.getCurrency() != null ? dto.getCurrency().trim() : "USD";
        if (currency.isEmpty()) {
            currency = "USD";
        }
        product.setCurrency(currency);

        product.setIsTaxable(dto.getIsTaxable() != null ? dto.getIsTaxable() : Boolean.TRUE);
        product.setTaxCode(dto.getTaxCode());
        product.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        Product savedProduct = productRepository.save(product);

        List<ProductImage> images = new ArrayList<>();
        for (ProductImageCreateDto imgDto : dto.getImage()) {
            ProductImage img = new ProductImage();
            img.setProduct(savedProduct);
            img.setImageUrl(imgDto.getImageUrl().trim());
            img.setImageOrder(imgDto.getImageOrder());
            img.setCreatedAt(now);
            images.add(img);
        }
        productImageRepository.saveAll(images);

        List<ProductType> types = productTypeRepository.findByTypeNameIn(dto.getTypeNames());
        if (types.size() != dto.getTypeNames().size()) {
            throw new IllegalArgumentException("Some product types were not found");
        }

        List<ProductsProductType> pptList = new ArrayList<>();
        for (ProductType type : types) {
            ProductsProductType ppt = new ProductsProductType();
            ppt.setId(new ProductsProductTypeId(savedProduct.getId(), type.getId()));
            ppt.setProduct(savedProduct);
            ppt.setType(type);
            pptList.add(ppt);
        }
        productsProductTypeRepository.saveAll(pptList);

        return mapToDto(savedProduct);
    }

    @Override
    public String suggestSku(List<String> typeNames) {
        Integer maxId = productRepository.findMaxId();
        int nextId = (maxId == null ? 1 : maxId + 1);

        if (typeNames == null || typeNames.isEmpty()) {
            return String.valueOf(nextId);
        }

        List<ProductType> types = productTypeRepository.findByTypeNameIn(typeNames);

        List<String> codes = types.stream()
                .map(t -> FormatTypeProduct.autoTypeCode(t.getTypeName()))
                .sorted()
                .toList();

        if (codes.isEmpty()) {
            return String.valueOf(nextId);
        }

        return nextId + "_" + String.join("_", codes);
    }

}
