import { RecipesController } from "../controllers/RecipesController";
import { body, param, query } from "express-validator";


const controller = new RecipesController();


export const RecipesRoutes = [
   {
       method: "get",
       route: "/recipes",
       action: controller.getAllRecipes,
       validation: []
   },
   {
       method: "get",
       route: "/recipes/id/:id",
       action: controller.getRecipeById,
       validation: [
           param("id").isMongoId()
       ]
   },
   {
        method: "get",
        route: "/recipes/name",
        action: controller.getRecipeByName,
        validation: [
            query("name").isString().notEmpty().withMessage("Recipe name is required.")
        ]
    },
   {
        method: "get",
        route: "/recipes/:id/getIngredientDetails",
        action: controller.getIngredientsFromRecipeId,
        validation: [
            param("id").isMongoId()
        ]
   },
   {
       method: "post",
       route: "/recipes",
       action: controller.postNewRecipe,
       validation: [
           body("name").isString(),
           body("ingredients").isArray(),
           body("procedure").isArray(),
           body("cuisineType").isString(),
           body("recipeComplexity").isString(),
           body("spiceLevel").isString(),
           body("preparationTime").isInt(),
           body("calories").isInt(),
           body("nutritionLevel").isString(),
           body("price").isInt()
       ]
   },
   {
       method: "post",
       route: "/recipes/AI",
       validation: [
           body("ingredients").isArray({ min: 1 }).withMessage("Ingredients must be a non-empty array"),
           body("ingredients.*").isString().withMessage("Each ingredient must be a string")
       ],
       action: controller.postNewRecipeFromAI
   },
   {
       method: "put",
       route: "/recipes/:_id",
       action: controller.putRecipeById,
       validation: [
           param("_id").isMongoId(),
           body("name").isString(),
           body("ingredients").isArray(),
           body("procedure").isArray(),
           body("cuisineType").isString(),
           body("recipeComplexity").isString(),
           body("spiceLevel").isString(),
           body("preparationTime").isInt(),
       ]
   },
   {
       method: "delete",
       route: "/recipes/:_id",
       action: controller.deleteRecipeById,
       validation: [
           param("_id").isMongoId()
       ]
   },
];