import {describe, expect, test, beforeAll, afterAll} from '@jest/globals';
import app from '../../index';
import request from "supertest";
import mongoose from "mongoose";

// Clean up after all tests
afterAll(async () => {
    await mongoose.connection.close();
    await new Promise(resolve => setTimeout(resolve, 1000));
});

describe("Unmocked: DELETE /ingredients/:id", () => {
    // Input: A DELETE request with an invalid MongoDB ID
    // Expected status code: 400
    // Expected behavior: Returns validation error for invalid ID format
    // Expected output: An object with 'errors' property
    test("deleteIngredientById with invalid ID", async () => {
        const res = await request(app)
            .delete('/ingredients/invalid-id');
        
        expect(res.status).toBe(400);
        expect(res.body).toHaveProperty('errors');
    });

    // Input: A DELETE request with a valid but non-existent MongoDB ID
    // Expected status code: 404
    // Expected behavior: Returns error indicating ingredient not found
    // Expected output: An error response
    test("deleteIngredientById with non-existent ID", async () => {
        const nonExistentId = new mongoose.Types.ObjectId().toString();
        
        const res = await request(app)
            .delete(`/ingredients/${nonExistentId}`);
        
        expect(res.status).toBe(404);
    });
}); 