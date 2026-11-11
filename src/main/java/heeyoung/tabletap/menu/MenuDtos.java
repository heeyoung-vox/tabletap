package heeyoung.tabletap.menu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class MenuDtos {

    // Keeps order totals far away from overflowing a long.
    static final long MAX_PRICE = 1_000_000_000_000L;

    private MenuDtos() {
    }

    public record CategoryRequest(@NotBlank @Size(max = 120) String name, int sortOrder) {
    }

    public record CategoryResponse(UUID id, String name, int sortOrder) {

        static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getSortOrder());
        }
    }

    public record ItemRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull @PositiveOrZero @Max(MAX_PRICE) Long basePrice,
            @Size(max = 500) String imageUrl,
            Boolean available,
            int sortOrder,
            List<@NotNull @Valid OptionGroupRequest> optionGroups) {

        public ItemRequest {
            available = available == null || available;
            optionGroups = optionGroups == null ? List.of() : optionGroups;
        }
    }

    public record OptionGroupRequest(
            @NotBlank @Size(max = 120) String name,
            @Min(0) int minSelect,
            @Min(1) int maxSelect,
            @NotEmpty List<@NotNull @Valid OptionRequest> options) {

        @AssertTrue(message = "maxSelect must be at least minSelect")
        boolean isRangeOrdered() {
            return maxSelect >= minSelect;
        }

        @AssertTrue(message = "minSelect cannot exceed the number of options")
        boolean isMinSelectReachable() {
            return options == null || minSelect <= options.size();
        }
    }

    public record OptionRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @PositiveOrZero @Max(MAX_PRICE) Long priceDelta,
            Boolean available) {

        public OptionRequest {
            available = available == null || available;
        }
    }

    public record AvailabilityRequest(@NotNull Boolean available) {
    }

    public record ItemResponse(
            UUID id,
            UUID categoryId,
            String name,
            String description,
            long basePrice,
            String imageUrl,
            boolean available,
            int sortOrder,
            List<OptionGroupResponse> optionGroups) {

        static ItemResponse from(MenuItem item) {
            return new ItemResponse(
                    item.getId(),
                    item.getCategory().getId(),
                    item.getName(),
                    item.getDescription(),
                    item.getBasePrice(),
                    item.getImageUrl(),
                    item.isAvailable(),
                    item.getSortOrder(),
                    item.getOptionGroups().stream().map(OptionGroupResponse::from).toList());
        }
    }

    public record OptionGroupResponse(
            UUID id, String name, int minSelect, int maxSelect, List<OptionResponse> options) {

        static OptionGroupResponse from(OptionGroup group) {
            return new OptionGroupResponse(
                    group.getId(),
                    group.getName(),
                    group.getMinSelect(),
                    group.getMaxSelect(),
                    group.getOptions().stream().map(OptionResponse::from).toList());
        }
    }

    public record OptionResponse(UUID id, String name, long priceDelta, boolean available) {

        static OptionResponse from(MenuOption option) {
            return new OptionResponse(
                    option.getId(), option.getName(), option.getPriceDelta(), option.isAvailable());
        }
    }
}
