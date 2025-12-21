package sunshine_dental_care.services.impl.admin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.adminDTO.ProductStatisticsDto;
import sunshine_dental_care.entities.Inventory;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.huybro_products.Product;
import sunshine_dental_care.repositories.InventoryRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.repositories.huybro_products.ProductRepository;
import sunshine_dental_care.services.interfaces.admin.AdminInventoryService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminInventoryServiceImpl implements AdminInventoryService {

    private final ProductRepository productRepo;
    private final EntityManager entityManager;
    private final InventoryRepo inventoryRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final UserRepo userRepo;

    @Override
    @Transactional(readOnly = true)
    public ProductStatisticsDto getProductStatistics() {
        log.debug("Fetching product statistics");

        ProductStatisticsDto stats = new ProductStatisticsDto();

        // Tổng quan sản phẩm
        long totalProducts = productRepo.count();
        long activeProducts = productRepo.countByIsActiveTrue();
        long inactiveProducts = totalProducts - activeProducts;

        stats.setTotalProducts(totalProducts);
        stats.setActiveProducts(activeProducts);
        stats.setInactiveProducts(inactiveProducts);

        // Thống kê tồn kho và sản phẩm sắp hết
        List<Product> allProducts = productRepo.findAll();
        List<Inventory> inventories = inventoryRepo.findAll();

        // Map productId -> tổng số lượng tồn
        Map<Integer, Integer> productStock = inventories.stream()
                .collect(Collectors.groupingBy(Inventory::getProductId,
                        Collectors.summingInt(inv -> inv.getQtyOnHand() != null ? inv.getQtyOnHand() : 0)));
        int totalStock = 0;
        int lowStockCount = 0;
        int outOfStockCount = 0;
        List<ProductStatisticsDto.LowStockProductDto> lowStockProducts = new ArrayList<>();

        for (Product product : allProducts) {
            int stock = productStock.getOrDefault(product.getId(), 0);
            totalStock += stock;

            if (stock == 0) {
                outOfStockCount++;
            } else if (stock <= 10) { // Ngưỡng sắp hết hàng có thể chỉnh sửa
                lowStockCount++;
                ProductStatisticsDto.LowStockProductDto lowStock = new ProductStatisticsDto.LowStockProductDto();
                lowStock.setProductId(product.getId());
                lowStock.setProductName(product.getProductName());
                lowStock.setSku(product.getSku());
                lowStock.setCurrentStock(stock);
                lowStock.setDefaultRetailPrice(product.getDefaultRetailPrice());
                lowStockProducts.add(lowStock);
            }
        }

        stats.setTotalStockQuantity(totalStock);
        stats.setLowStockProductsCount(lowStockCount);
        stats.setOutOfStockProductsCount(outOfStockCount);
        stats.setLowStockProducts(lowStockProducts.stream()
                .sorted((a, b) -> Integer.compare(a.getCurrentStock(), b.getCurrentStock()))
                .limit(20) // Lấy top 20 sản phẩm sắp hết hàng
                .collect(Collectors.toList()));

        // Thống kê doanh thu - sản phẩm bán theo clinic
        List<ProductStatisticsDto.ClinicSalesDto> salesByClinic = getSalesByClinic();
        stats.setSalesByClinic(salesByClinic);

        // Top sản phẩm bán chạy nhất
        List<ProductStatisticsDto.TopProductDto> topProducts = getTopSellingProducts();
        stats.setTopSellingProducts(topProducts);

        return stats;
    }

    // Lấy thống kê bán hàng theo clinic (số lượng, doanh thu, số sp bán)
    private List<ProductStatisticsDto.ClinicSalesDto> getSalesByClinic() {
        String jpql = """
            SELECT 
                i.invoice.clinic.id as clinicId,
                i.invoice.clinic.clinicName as clinicName,
                SUM(i.quantity) as totalItemsSold,
                SUM(i.lineTotalAmount) as totalRevenue,
                COUNT(DISTINCT i.product.id) as uniqueProductsSold
            FROM ProductInvoiceItem i
            WHERE i.invoice.clinic IS NOT NULL
            GROUP BY i.invoice.clinic.id, i.invoice.clinic.clinicName
            ORDER BY totalItemsSold DESC
            """;

        TypedQuery<Object[]> query = entityManager.createQuery(jpql, Object[].class);
        List<Object[]> results = query.getResultList();

        List<ProductStatisticsDto.ClinicSalesDto> clinicSales = new ArrayList<>();
        for (Object[] row : results) {
            ProductStatisticsDto.ClinicSalesDto dto = new ProductStatisticsDto.ClinicSalesDto();
            Integer clinicId = (Integer) row[0];
            dto.setClinicId(clinicId);
            dto.setClinicName((String) row[1]);
            dto.setTotalItemsSold(((Number) row[2]).longValue());
            dto.setTotalRevenue((BigDecimal) row[3]);
            dto.setUniqueProductsSold(((Number) row[4]).longValue());
            
            // Tính số bác sĩ và nhân viên đang hoạt động tại phòng khám này
            int doctorsCount = countActiveDoctorsByClinic(clinicId);
            int employeesCount = countActiveEmployeesByClinic(clinicId);
            
            dto.setActiveDoctorsCount(doctorsCount);
            dto.setActiveEmployeesCount(employeesCount);
            
            clinicSales.add(dto);
        }

        return clinicSales;
    }

    // Đếm số bác sĩ đang hoạt động tại phòng khám
    private int countActiveDoctorsByClinic(Integer clinicId) {
        try {
            // Tối ưu: Dùng query COUNT thay vì load toàn bộ danh sách
            String jpql = """
                SELECT COUNT(DISTINCT u.id)
                FROM User u
                JOIN u.userRoles ur
                WHERE ur.role.id = 3
                  AND u.isActive = true
                  AND EXISTS (
                      SELECT uca FROM UserClinicAssignment uca 
                      WHERE uca.user.id = u.id AND uca.clinic.id = :clinicId
                  )
                """;
            jakarta.persistence.TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
            query.setParameter("clinicId", clinicId);
            Long count = query.getSingleResult();
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.warn("Error counting doctors for clinic {}: {}", clinicId, e.getMessage());
            return 0;
        }
    }

    // Đếm số nhân viên đang hoạt động tại phòng khám (cả bác sĩ)
    private int countActiveEmployeesByClinic(Integer clinicId) {
        try {
            List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByClinicId(clinicId);
            if (assignments == null || assignments.isEmpty()) {
                return 0;
            }
            
            // Lọc các user còn hoạt động và chưa kết thúc assignment
            long count = assignments.stream()
                    .filter(assignment -> {
                        User user = assignment.getUser();
                        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
                            return false;
                        }
                        if (assignment.getEndDate() != null) {
                            return assignment.getEndDate().isAfter(java.time.LocalDate.now()) 
                                || assignment.getEndDate().equals(java.time.LocalDate.now());
                        }
                        return true;
                    })
                    .map(assignment -> assignment.getUser().getId())
                    .distinct()
                    .count();
            
            return (int) count;
        } catch (Exception e) {
            log.warn("Error counting employees for clinic {}: {}", clinicId, e.getMessage());
            return 0;
        }
    }

    // Lấy top sản phẩm bán chạy nhất
    private List<ProductStatisticsDto.TopProductDto> getTopSellingProducts() {
        String jpql = """
            SELECT 
                i.product.id as productId,
                i.product.productName as productName,
                i.product.sku as sku,
                SUM(i.quantity) as totalQuantitySold,
                SUM(i.lineTotalAmount) as totalRevenue,
                i.product.unit as currentStock
            FROM ProductInvoiceItem i
            WHERE i.product IS NOT NULL
            GROUP BY i.product.id, i.product.productName, i.product.sku, i.product.unit
            ORDER BY totalQuantitySold DESC
            """;

        TypedQuery<Object[]> query = entityManager.createQuery(jpql, Object[].class);
        query.setMaxResults(10); // Lấy top 10 sản phẩm bán chạy
        List<Object[]> results = query.getResultList();

        List<ProductStatisticsDto.TopProductDto> topProducts = new ArrayList<>();
        for (Object[] row : results) {
            ProductStatisticsDto.TopProductDto dto = new ProductStatisticsDto.TopProductDto();
            dto.setProductId((Integer) row[0]);
            dto.setProductName((String) row[1]);
            dto.setSku((String) row[2]);
            dto.setTotalQuantitySold(((Number) row[3]).longValue());
            dto.setTotalRevenue((BigDecimal) row[4]);
            dto.setCurrentStock((Integer) row[5]);
            topProducts.add(dto);
        }

        return topProducts;
    }
}
