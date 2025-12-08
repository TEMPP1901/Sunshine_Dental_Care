package sunshine_dental_care.dto.adminDTO;

import java.math.BigDecimal;
import java.util.List;

public class ProductStatisticsDto {
    // Tổng quan
    private Long totalProducts;
    private Long activeProducts;
    private Long inactiveProducts;
    private Integer totalStockQuantity; // Tổng số lượng tồn kho
    private Integer lowStockProductsCount; // Số sản phẩm sắp hết (<= 10)
    private Integer outOfStockProductsCount; // Số sản phẩm hết hàng (= 0)

    // Thống kê bán hàng theo clinic
    private List<ClinicSalesDto> salesByClinic;

    // Top sản phẩm bán chạy
    private List<TopProductDto> topSellingProducts;

    // Sản phẩm sắp hết
    private List<LowStockProductDto> lowStockProducts;

    // Getters and Setters
    public Long getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(Long totalProducts) {
        this.totalProducts = totalProducts;
    }

    public Long getActiveProducts() {
        return activeProducts;
    }

    public void setActiveProducts(Long activeProducts) {
        this.activeProducts = activeProducts;
    }

    public Long getInactiveProducts() {
        return inactiveProducts;
    }

    public void setInactiveProducts(Long inactiveProducts) {
        this.inactiveProducts = inactiveProducts;
    }

    public Integer getTotalStockQuantity() {
        return totalStockQuantity;
    }

    public void setTotalStockQuantity(Integer totalStockQuantity) {
        this.totalStockQuantity = totalStockQuantity;
    }

    public Integer getLowStockProductsCount() {
        return lowStockProductsCount;
    }

    public void setLowStockProductsCount(Integer lowStockProductsCount) {
        this.lowStockProductsCount = lowStockProductsCount;
    }

    public Integer getOutOfStockProductsCount() {
        return outOfStockProductsCount;
    }

    public void setOutOfStockProductsCount(Integer outOfStockProductsCount) {
        this.outOfStockProductsCount = outOfStockProductsCount;
    }

    public List<ClinicSalesDto> getSalesByClinic() {
        return salesByClinic;
    }

    public void setSalesByClinic(List<ClinicSalesDto> salesByClinic) {
        this.salesByClinic = salesByClinic;
    }

    public List<TopProductDto> getTopSellingProducts() {
        return topSellingProducts;
    }

    public void setTopSellingProducts(List<TopProductDto> topSellingProducts) {
        this.topSellingProducts = topSellingProducts;
    }

    public List<LowStockProductDto> getLowStockProducts() {
        return lowStockProducts;
    }

    public void setLowStockProducts(List<LowStockProductDto> lowStockProducts) {
        this.lowStockProducts = lowStockProducts;
    }

    // Inner DTOs
    public static class ClinicSalesDto {
        private Integer clinicId;
        private String clinicName;
        private Long totalItemsSold; // Tổng số lượng sản phẩm đã bán
        private BigDecimal totalRevenue; // Tổng doanh thu
        private Long uniqueProductsSold; // Số sản phẩm khác nhau đã bán
        private Integer activeDoctorsCount; // Số bác sĩ đang hoạt động
        private Integer activeEmployeesCount; // Số nhân viên đang hoạt động

        public Integer getClinicId() {
            return clinicId;
        }

        public void setClinicId(Integer clinicId) {
            this.clinicId = clinicId;
        }

        public String getClinicName() {
            return clinicName;
        }

        public void setClinicName(String clinicName) {
            this.clinicName = clinicName;
        }

        public Long getTotalItemsSold() {
            return totalItemsSold;
        }

        public void setTotalItemsSold(Long totalItemsSold) {
            this.totalItemsSold = totalItemsSold;
        }

        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }

        public void setTotalRevenue(BigDecimal totalRevenue) {
            this.totalRevenue = totalRevenue;
        }

        public Long getUniqueProductsSold() {
            return uniqueProductsSold;
        }

        public void setUniqueProductsSold(Long uniqueProductsSold) {
            this.uniqueProductsSold = uniqueProductsSold;
        }

        public Integer getActiveDoctorsCount() {
            return activeDoctorsCount;
        }

        public void setActiveDoctorsCount(Integer activeDoctorsCount) {
            this.activeDoctorsCount = activeDoctorsCount;
        }

        public Integer getActiveEmployeesCount() {
            return activeEmployeesCount;
        }

        public void setActiveEmployeesCount(Integer activeEmployeesCount) {
            this.activeEmployeesCount = activeEmployeesCount;
        }
    }

    public static class TopProductDto {
        private Integer productId;
        private String productName;
        private String sku;
        private Long totalQuantitySold;
        private BigDecimal totalRevenue;
        private Integer currentStock;

        public Integer getProductId() {
            return productId;
        }

        public void setProductId(Integer productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public Long getTotalQuantitySold() {
            return totalQuantitySold;
        }

        public void setTotalQuantitySold(Long totalQuantitySold) {
            this.totalQuantitySold = totalQuantitySold;
        }

        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }

        public void setTotalRevenue(BigDecimal totalRevenue) {
            this.totalRevenue = totalRevenue;
        }

        public Integer getCurrentStock() {
            return currentStock;
        }

        public void setCurrentStock(Integer currentStock) {
            this.currentStock = currentStock;
        }
    }

    public static class LowStockProductDto {
        private Integer productId;
        private String productName;
        private String sku;
        private Integer currentStock;
        private BigDecimal defaultRetailPrice;

        public Integer getProductId() {
            return productId;
        }

        public void setProductId(Integer productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public Integer getCurrentStock() {
            return currentStock;
        }

        public void setCurrentStock(Integer currentStock) {
            this.currentStock = currentStock;
        }

        public BigDecimal getDefaultRetailPrice() {
            return defaultRetailPrice;
        }

        public void setDefaultRetailPrice(BigDecimal defaultRetailPrice) {
            this.defaultRetailPrice = defaultRetailPrice;
        }
    }
}
