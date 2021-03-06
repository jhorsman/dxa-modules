package com.sdl.dxa.modules.smarttarget.mapping;

import com.sdl.dxa.modules.smarttarget.model.entity.AbstractSmartTargetPageModel;
import com.sdl.dxa.modules.smarttarget.model.entity.SmartTargetExperiment;
import com.sdl.dxa.modules.smarttarget.model.entity.SmartTargetItem;
import com.sdl.dxa.modules.smarttarget.model.entity.SmartTargetPageModel;
import com.sdl.dxa.modules.smarttarget.model.entity.SmartTargetPromotion;
import com.sdl.dxa.modules.smarttarget.model.entity.SmartTargetRegion;
import com.sdl.webapp.common.api.localization.Localization;
import com.sdl.webapp.common.api.model.PageModel;
import com.sdl.webapp.common.api.model.mvcdata.DefaultsMvcData;
import com.sdl.webapp.util.dd4t.TcmUtils;
import com.tridion.ambientdata.AmbientDataContext;
import com.tridion.ambientdata.claimstore.ClaimStore;
import com.tridion.smarttarget.SmartTargetException;
import com.tridion.smarttarget.analytics.tracking.ExperimentDimensions;
import com.tridion.smarttarget.query.Experiment;
import com.tridion.smarttarget.query.ExperimentCookie;
import com.tridion.smarttarget.query.Item;
import com.tridion.smarttarget.query.Promotion;
import com.tridion.smarttarget.query.ResultSet;
import com.tridion.smarttarget.query.ResultSetImpl;
import com.tridion.smarttarget.query.builder.PageCriteria;
import com.tridion.smarttarget.query.builder.PublicationCriteria;
import com.tridion.smarttarget.query.builder.QueryBuilder;
import com.tridion.smarttarget.query.builder.RegionCriteria;
import com.tridion.smarttarget.utils.AmbientDataHelper;
import com.tridion.smarttarget.utils.CookieProcessor;
import com.tridion.smarttarget.utils.TcmUri;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sdl.webapp.common.api.model.mvcdata.MvcDataCreator.creator;

@Component
@Slf4j
@SuppressWarnings("Duplicates")
public class SmartTargetPageBuilder extends AbstractSmartTargetPageBuilder {

    private static SmartTargetPromotion createPromotionEntity(final Promotion promotion, final String promotionViewName,
                                                              final String regionName, ExperimentDimensions experimentDimensions,
                                                              final Localization localization) throws SmartTargetException {

        SmartTargetPromotion smartTargetPromotion = promotion instanceof Experiment ?
                new SmartTargetExperiment(experimentDimensions) : new SmartTargetPromotion();

        smartTargetPromotion.setMvcData(creator()
                .defaults(DefaultsMvcData.CORE_ENTITY)
                .mergeIn(creator().fromQualifiedName(promotionViewName).create())
                .create());

        Map<String, Object> xpmMetadata = new HashMap<>(2);
        xpmMetadata.put("PromotionID", promotion.getPromotionId());
        xpmMetadata.put("RegionID", regionName);

        smartTargetPromotion.setXpmMetadata(xpmMetadata);

        smartTargetPromotion.setTitle(promotion.getTitle());
        smartTargetPromotion.setSlogan(promotion.getSlogan());
        smartTargetPromotion.setId(promotion.getPromotionId());

        // filter items out and convert to SmartTargetItem
        List<SmartTargetItem> smartTargetItems = new ArrayList<>(promotion.getItems().size());
        for (Item item : promotion.getItems()) {
            if (!item.isVisible()) {
                continue;
            }

            int itemId = item.getComponentUri().getItemId();
            String id = String.format("%s-%s", itemId, item.getTemplateUri().getItemId());

            smartTargetItems.add(new SmartTargetItem(id, localization));
        }
        smartTargetPromotion.setItems(smartTargetItems);

        return smartTargetPromotion;
    }

    private static ExperimentDimensions getExperimentDimensions(Localization localization, AbstractSmartTargetPageModel stPageModel, String currentRegionName) {
        ExperimentDimensions experimentDimensions = new ExperimentDimensions();
        experimentDimensions.setPublicationId(TcmUtils.buildPublicationTcmUri(localization.getId()));
        experimentDimensions.setPageId(stPageModel.getId());
        experimentDimensions.setRegion(currentRegionName);
        return experimentDimensions;
    }

    private static boolean filterResultSet(AbstractSmartTargetPageModel stPageModel, List<Promotion> promotions,
                                           SmartTargetRegion smartTargetRegion, List<String> itemsAlreadyOnPage,
                                           ExperimentDimensions experimentDimensions, ExperimentCookies experimentCookies) {
        try {
            ResultSetImpl.filterPromotions(promotions,
                    smartTargetRegion.getName(),
                    smartTargetRegion.getMaxItems(),
                    stPageModel.isAllowDuplicates(),

                    new ArrayList<String>(),

                    itemsAlreadyOnPage,
                    experimentCookies.existingCookies,
                    experimentCookies.newCookies,

                    experimentDimensions);
        } catch (SmartTargetException e) {
            log.error("Smart target exception while filtering ResultSet from ST", e);
            return false;
        }
        return true;
    }

    private static boolean isPromotionToSkip(SmartTargetRegion smartTargetRegion, Promotion promotion) {
        return !promotion.isVisible() || !promotion.supportsRegion(smartTargetRegion.getName());
    }

    @Override
    protected AbstractSmartTargetPageModel getSmartTargetPageModel(PageModel pageModel) {
        return new SmartTargetPageModel(pageModel);
    }

    @SneakyThrows(ParseException.class)
    @Override
    protected void processQueryAndPromotions(Localization localization, AbstractSmartTargetPageModel stPageModel, String promotionViewName) {
        try {
            TcmUri pageUri = new TcmUri(stPageModel.getId());

            final ResultSet resultSet = executeSmartTargetQuery(stPageModel, pageUri);

            if (resultSet == null) {
                log.warn("SmartTarget API returned null as a result for query. This can be because of timeout. Skipping processing promotions.");
                return;
            }

            @NonNull final List<Promotion> promotions;
            if (resultSet.getPromotions() == null) {
                promotions = Collections.emptyList();
            } else {
                promotions = resultSet.getPromotions();
            }

            log.debug("SmartTarget query returned {} Promotions.", promotions.size());

            // Filter the Promotions for each SmartTargetRegion
            filterPromotionsForPage(localization, ((SmartTargetPageModel) stPageModel), promotions, promotionViewName);

        } catch (SmartTargetException e) {
            log.error("Smart target exception", e);
        }
    }

    @SneakyThrows(ParseException.class)
    private ResultSet executeSmartTargetQuery(AbstractSmartTargetPageModel stPageModel, final TcmUri pageUri) throws SmartTargetException {
        TcmUri publicationUri = new TcmUri(TcmUtils.buildPublicationTcmUri(pageUri.getPublicationId()));

        ClaimStore claimStore = AmbientDataContext.getCurrentClaimStore();
        String triggers = AmbientDataHelper.getTriggers(claimStore);

        QueryBuilder queryBuilder = new QueryBuilder();
        queryBuilder.parseQueryString(triggers);
        queryBuilder
                .addCriteria(new PublicationCriteria(publicationUri))
                .addCriteria(new PageCriteria(pageUri));

        // Adding all the page regions to the query for having only 1 query a page
        for (SmartTargetRegion region : stPageModel.getRegions().get(SmartTargetRegion.class)) {
            queryBuilder.addCriteria(new RegionCriteria(region.getName()));
        }

        return queryBuilder.execute();
    }

    private void filterPromotionsForPage(Localization localization, SmartTargetPageModel stPageModel,
                                         final List<Promotion> promotions, String promotionViewName) throws SmartTargetException {
//        // TODO: we shouldn't access ServletRequest in a Model Builder.
        Map<String, ExperimentCookie> existingExperimentCookies = CookieProcessor.getExperimentCookies(httpServletRequest);
        Map<String, ExperimentCookie> newExperimentCookies = new HashMap<>();

        List<String> itemsAlreadyOnPage = new ArrayList<>();

        for (final SmartTargetRegion smartTargetRegion : stPageModel.getRegions().get(SmartTargetRegion.class)) {
            final String currentRegionName = smartTargetRegion.getName();
            ExperimentDimensions experimentDimensions = getExperimentDimensions(localization, stPageModel, currentRegionName);

            if (!filterResultSet(stPageModel, promotions, smartTargetRegion, itemsAlreadyOnPage, experimentDimensions,
                    ExperimentCookies.builder().newCookies(newExperimentCookies)
                            .existingCookies(existingExperimentCookies).build())) {
                return;
            }

            setXpmMetadataForStaging(localization,
                    ResultSetImpl.getExperienceManagerMarkup(currentRegionName, smartTargetRegion.getMaxItems(), promotions), smartTargetRegion);

            // Create SmartTargetPromotion Entity Models for visible Promotions in the current SmartTargetRegion.
            // It seems that ResultSet.FilterPromotions doesn't really filter on Region name, so we do post-filtering here.
            for (Promotion promotion : promotions) {
                if (isPromotionToSkip(smartTargetRegion, promotion)) {
                    continue;
                }

                // if we found promotions in ST then we should filter fallback content out first
                clearFallbackContentIfNeeded(smartTargetRegion);

                SmartTargetPromotion promotionEntity = createPromotionEntity(promotion, promotionViewName,
                        currentRegionName, experimentDimensions, localization);
                smartTargetRegion.addEntity(promotionEntity);
            }
        }

        stPageModel.setNewExperimentCookies(newExperimentCookies);
    }

    @Builder
    private static class ExperimentCookies {
        Map<String, ExperimentCookie> existingCookies, newCookies;
    }

}
