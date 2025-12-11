package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.configuration.ApprovalConfiguration;
import de.seuhd.campuscoffee.domain.exceptions.DuplicationException;
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException;
import de.seuhd.campuscoffee.domain.exceptions.ValidationException;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.model.objects.Review;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.ports.api.ReviewService;
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService;
import de.seuhd.campuscoffee.domain.ports.data.PosDataService;
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService;
import de.seuhd.campuscoffee.domain.ports.data.UserDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of the Review service that handles business logic related to
 * review entities.
 */
@Slf4j
@Service
public class ReviewServiceImpl extends CrudServiceImpl<Review, Long> implements ReviewService {
    private final ReviewDataService reviewDataService;
    private final UserDataService userDataService;
    private final PosDataService posDataService;
    private final ApprovalConfiguration approvalConfiguration;

    public ReviewServiceImpl(@NonNull ReviewDataService reviewDataService,
            @NonNull UserDataService userDataService,
            @NonNull PosDataService posDataService,
            @NonNull ApprovalConfiguration approvalConfiguration) {
        super(Review.class);
        this.reviewDataService = reviewDataService;
        this.userDataService = userDataService;
        this.posDataService = posDataService;
        this.approvalConfiguration = approvalConfiguration;
    }

    @Override
    protected CrudDataService<Review, Long> dataService() {
        return reviewDataService;
    }

    @Override
    @Transactional
    public @NonNull Review upsert(@NonNull Review review) {
        if (review.id() == null) {

            User user;
            Pos pos;

            try {
                user = userDataService.getById(review.authorId());
            } catch (NotFoundException e) {
                log.error("Author of review does not exist");
                throw e;
            }

            try {
                pos = posDataService.getById(review.posId());
            } catch (NotFoundException e) {
                log.error("POS to create a review for does not exists");
                throw e;
            }

            Integer count = reviewDataService.filter(pos, user).size();

            if (count > 0) {
                log.error("Users can only a review a POS once");
                throw new ValidationException("Users can only review a POS once");
            }

            log.info("Creating new review: {}", review.id());
        } else {
            log.info("Updating review with ID: {}", review.id());
            Objects.requireNonNull(review.id());
            reviewDataService.getById(review.id());
        }

        return super.upsert(review);
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Review> filter(@NonNull Long posId, @NonNull Boolean approved) {
        return reviewDataService.filter(posDataService.getById(posId), approved);
    }

    @Override
    @Transactional
    public @NonNull Review approve(@NonNull Review review, @NonNull Long userId) {
        log.info("Processing approval request for review with ID '{}' by user with ID '{}'...",
                review.getId(), userId);

        try {
            userDataService.getById(userId);
            try {
                reviewDataService.getById(review.id());

                if (review.author().id() == userId) {
                    log.error("User tried to approve own review");
                    throw new ValidationException("Users cannot approve their own review.");
                }
                review = review.toBuilder()
                        .approvalCount(review.approvalCount() + 1)
                        .build();

                review = updateApprovalStatus(review);

            } catch (NotFoundException e) {
                log.error("Could not find review with ID: {}", review.id());
                throw e;
            }
        } catch (NotFoundException e) {
            log.error("Could not find user with ID: {}", userId);
            throw e;
        }

        return reviewDataService.upsert(review);
    }

    /**
     * Calculates and updates the approval status of a review based on the approval
     * count.
     * Business rule: A review is approved when it reaches the configured minimum
     * approval count threshold.
     *
     * @param review The review to calculate approval status for
     * @return The review with updated approval status
     */
    Review updateApprovalStatus(Review review) {
        log.debug("Updating approval status of review with ID '{}'...", review.getId());
        return review.toBuilder()
                .approved(isApproved(review))
                .build();
    }

    /**
     * Determines if a review meets the minimum approval threshold.
     * 
     * @param review The review to check
     * @return true if the review meets or exceeds the minimum approval count, false
     *         otherwise
     */
    private boolean isApproved(Review review) {
        return review.approvalCount() >= approvalConfiguration.minCount();
    }
}
