package com.xiguli.langhuan.engine
import org.junit.Assert.*
import org.junit.Test
class CreativeDiscussionRoutingV135Test {
 @Test fun explicitDiscussionDoesNotRewriteScenesOrProse() {
   assertTrue(WorkspaceNaturalLanguageRouter.route("只讨论一下：这个场景可以怎样写得更自然？").isDiscussionOnly)
 }
 @Test fun explicitDiscussionDoesNotCreateCanonProposal() {
   assertTrue(WorkspaceNaturalLanguageRouter.route("请先讨论：修改人物设定会有什么影响？").isDiscussionOnly)
 }
 @Test fun negativeExecutionFollowedByDiscussionStaysReadOnly() {
   assertTrue(WorkspaceNaturalLanguageRouter.route("不要重写本章，只讨论怎样把语气写得温和。").isDiscussionOnly)
 }
 @Test fun explicitDraftRequestStillExecutes() {
   assertTrue(WorkspaceNaturalLanguageRouter.route("重写本章正文，让对白自然一些。").hasProseMutation)
 }
 @Test fun explicitSceneRequestStillExecutes() {
   assertTrue(WorkspaceNaturalLanguageRouter.route("把第三场提前，调整场景顺序。").hasSceneMutation)
 }
 @Test fun explicitCanonRequestStillCreatesPreview() {
   assertTrue(WorkspaceNaturalLanguageRouter.route("修改人物设定，让主角更谨慎。").requestsCanonProposal)
 }
 @Test fun ordinaryReviewStillReviews() {
   assertTrue(WorkspaceNaturalLanguageRouter.route("检查本章时间线有没有冲突。").isReviewOnly)
 }
}
